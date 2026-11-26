package tunnel

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"time"

	clashHttp "github.com/metacubex/mihomo/component/http"
)

const (
	ipQualityTimeout = 25 * time.Second
	ipQualityMaxBody = 1 << 20
	ipEchoURL        = "https://api.ip.sb/ip"
)

// browserUserAgent avoids naive bot filtering on some endpoints
const browserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

// IPQuality holds the aggregated risk info of an IP from multiple databases.
type IPQuality struct {
	IP  string `json:"ip"`
	ASN string `json:"asn,omitempty"`

	ScamalyticsScore string            `json:"scamalyticsScore,omitempty"`
	ScamalyticsRisk  string            `json:"scamalyticsRisk,omitempty"`
	ProxyCheckRisk   string            `json:"proxyCheckRisk,omitempty"`
	ProxyCheckProxy  string            `json:"proxyCheckProxy,omitempty"`
	ProxyCheckType   string            `json:"proxyCheckType,omitempty"`
	IPApiProxy       string            `json:"ipApiProxy,omitempty"`
	IPApiHosting     string            `json:"ipApiHosting,omitempty"`
	IPApiMobile      string            `json:"ipApiMobile,omitempty"`
	IPApiISP         string            `json:"ipApiISP,omitempty"`
	Sources          []IPQualitySource `json:"sources,omitempty"`
}

type IPQualitySource struct {
	Name      string  `json:"name"`
	Country   string  `json:"country,omitempty"`
	Region    string  `json:"region,omitempty"`
	City      string  `json:"city,omitempty"`
	ISP       string  `json:"isp,omitempty"`
	ASN       string  `json:"asn,omitempty"`
	Risk      string  `json:"risk,omitempty"`
	Proxy     string  `json:"proxy,omitempty"`
	Hosting   string  `json:"hosting,omitempty"`
	Type      string  `json:"type,omitempty"`
	Latitude  float64 `json:"latitude,omitempty"`
	Longitude float64 `json:"longitude,omitempty"`
}

var (
	scamalyticsScoreRe = regexp.MustCompile(`Fraud Score:\s*(\d+)`)
	scamalyticsRiskRe  = regexp.MustCompile(`>\s*(Low|Medium|High|Very High)\s+Risk\s*<`)
)

func httpGet(ctx context.Context, url string) ([]byte, int, error) {
	response, err := clashHttp.HttpRequest(ctx, url, http.MethodGet,
		http.Header{"User-Agent": {browserUserAgent}}, nil)
	if err != nil {
		return nil, 0, err
	}
	defer response.Body.Close()

	body, err := io.ReadAll(io.LimitReader(response.Body, ipQualityMaxBody))
	if err != nil {
		return nil, response.StatusCode, err
	}

	return body, response.StatusCode, nil
}

func boolText(v bool) string {
	if v {
		return "true"
	}
	return "false"
}

// resolveExitIP fetches the current exit IP as seen through the tunnel.
func resolveExitIP(ctx context.Context) string {
	body, status, err := httpGet(ctx, ipEchoURL)
	if err != nil || status != http.StatusOK {
		return ""
	}

	return strings.TrimSpace(string(body))
}

type scamalyticsResult struct {
	score string
	risk  string
}

func fetchScamalytics(ctx context.Context, ip string) (scamalyticsResult, error) {
	body, status, err := httpGet(ctx, "https://scamalytics.com/ip/"+ip)
	if err != nil {
		return scamalyticsResult{}, err
	}

	if status != http.StatusOK {
		return scamalyticsResult{}, fmt.Errorf("scamalytics: status %d", status)
	}

	html := string(body)

	result := scamalyticsResult{}

	if m := scamalyticsScoreRe.FindStringSubmatch(html); m != nil {
		result.score = m[1]
	}

	if m := scamalyticsRiskRe.FindStringSubmatch(html); m != nil {
		result.risk = m[1] + " Risk"
	}

	if result.score == "" && result.risk == "" {
		return scamalyticsResult{}, fmt.Errorf("scamalytics: parse failed")
	}

	return result, nil
}

type proxyCheckResult struct {
	risk  string
	proxy string
	typ   string
}

func fetchProxyCheck(ctx context.Context, ip string) (proxyCheckResult, error) {
	body, status, err := httpGet(ctx, "https://proxycheck.io/v2/"+ip+"?vpn=1&asn=1&risk=1")
	if err != nil {
		return proxyCheckResult{}, err
	}

	if status != http.StatusOK {
		return proxyCheckResult{}, fmt.Errorf("proxycheck: status %d", status)
	}

	var outer map[string]json.RawMessage
	if err := json.Unmarshal(body, &outer); err != nil {
		return proxyCheckResult{}, err
	}

	entry, ok := outer[ip]
	if !ok {
		return proxyCheckResult{}, fmt.Errorf("proxycheck: no entry")
	}

	var info struct {
		Proxy string      `json:"proxy"`
		Type  string      `json:"type"`
		Risk  json.Number `json:"risk"`
	}
	if err := json.Unmarshal(entry, &info); err != nil {
		return proxyCheckResult{}, err
	}

	return proxyCheckResult{
		risk:  info.Risk.String(),
		proxy: info.Proxy,
		typ:   info.Type,
	}, nil
}

type ipApiResult struct {
	proxy   string
	hosting string
	mobile  string
	as      string
	isp     string
}

func fetchIPApi(ctx context.Context, ip string) (ipApiResult, error) {
	body, status, err := httpGet(ctx, "http://ip-api.com/json/"+ip+"?fields=status,message,isp,as,mobile,proxy,hosting")
	if err != nil {
		return ipApiResult{}, err
	}

	if status != http.StatusOK {
		return ipApiResult{}, fmt.Errorf("ip-api: status %d", status)
	}

	var info struct {
		Status  string `json:"status"`
		Message string `json:"message"`
		Proxy   bool   `json:"proxy"`
		Hosting bool   `json:"hosting"`
		Mobile  bool   `json:"mobile"`
		As      string `json:"as"`
		Isp     string `json:"isp"`
	}
	if err := json.Unmarshal(body, &info); err != nil {
		return ipApiResult{}, err
	}

	if info.Status != "success" {
		return ipApiResult{}, fmt.Errorf("ip-api: %s", info.Message)
	}

	return ipApiResult{
		proxy:   boolText(info.Proxy),
		hosting: boolText(info.Hosting),
		mobile:  boolText(info.Mobile),
		as:      info.As,
		isp:     info.Isp,
	}, nil
}

func fetchIPWhoIs(ctx context.Context, ip string) (IPQualitySource, error) {
	body, status, err := httpGet(ctx, "https://ipwho.is/"+ip)
	if err != nil {
		return IPQualitySource{}, err
	}
	if status != http.StatusOK {
		return IPQualitySource{}, fmt.Errorf("ipwho.is: status %d", status)
	}
	var raw struct {
		Success    bool    `json:"success"`
		Message    string  `json:"message"`
		Type       string  `json:"type"`
		Country    string  `json:"country"`
		Region     string  `json:"region"`
		City       string  `json:"city"`
		Latitude   float64 `json:"latitude"`
		Longitude  float64 `json:"longitude"`
		Connection struct {
			ASN int    `json:"asn"`
			Org string `json:"org"`
			ISP string `json:"isp"`
		} `json:"connection"`
		Security struct {
			Proxy   bool `json:"proxy"`
			VPN     bool `json:"vpn"`
			Tor     bool `json:"tor"`
			Hosting bool `json:"hosting"`
		} `json:"security"`
	}
	if err := json.Unmarshal(body, &raw); err != nil {
		return IPQualitySource{}, err
	}
	if !raw.Success {
		return IPQualitySource{}, fmt.Errorf("ipwho.is: %s", raw.Message)
	}
	asn := ""
	if raw.Connection.ASN != 0 {
		asn = fmt.Sprintf("AS%d", raw.Connection.ASN)
		if raw.Connection.Org != "" {
			asn += " " + raw.Connection.Org
		}
	}
	isp := raw.Connection.ISP
	if isp == "" {
		isp = raw.Connection.Org
	}
	risk := ""
	if raw.Security.Proxy || raw.Security.VPN || raw.Security.Tor {
		risk = "proxy"
	} else if raw.Security.Hosting {
		risk = "hosting"
	}
	return IPQualitySource{
		Name:      "ipwho.is",
		Country:   raw.Country,
		Region:    raw.Region,
		City:      raw.City,
		ISP:       isp,
		ASN:       asn,
		Risk:      risk,
		Proxy:     boolText(raw.Security.Proxy || raw.Security.VPN || raw.Security.Tor),
		Hosting:   boolText(raw.Security.Hosting),
		Type:      raw.Type,
		Latitude:  raw.Latitude,
		Longitude: raw.Longitude,
	}, nil
}

func fetchIPAPICo(ctx context.Context, ip string) (IPQualitySource, error) {
	body, status, err := httpGet(ctx, "https://ipapi.co/"+ip+"/json/")
	if err != nil {
		return IPQualitySource{}, err
	}
	if status != http.StatusOK {
		return IPQualitySource{}, fmt.Errorf("ipapi.co: status %d", status)
	}
	var raw struct {
		Error       bool    `json:"error"`
		Reason      string  `json:"reason"`
		IP          string  `json:"ip"`
		Version     string  `json:"version"`
		City        string  `json:"city"`
		Region      string  `json:"region"`
		Country     string  `json:"country"`
		CountryName string  `json:"country_name"`
		ASN         string  `json:"asn"`
		Org         string  `json:"org"`
		Latitude    float64 `json:"latitude"`
		Longitude   float64 `json:"longitude"`
	}
	if err := json.Unmarshal(body, &raw); err != nil {
		return IPQualitySource{}, err
	}
	if raw.Error {
		return IPQualitySource{}, fmt.Errorf("ipapi.co: %s", raw.Reason)
	}
	country := raw.CountryName
	if country == "" {
		country = raw.Country
	}
	return IPQualitySource{
		Name:      "ipapi.co",
		Country:   country,
		Region:    raw.Region,
		City:      raw.City,
		ASN:       raw.ASN,
		ISP:       raw.Org,
		Type:      raw.Version,
		Latitude:  raw.Latitude,
		Longitude: raw.Longitude,
	}, nil
}

func fetchIPInfoIO(ctx context.Context, ip string) (IPQualitySource, error) {
	body, status, err := httpGet(ctx, "https://ipinfo.io/"+ip+"/json")
	if err != nil {
		return IPQualitySource{}, err
	}
	if status != http.StatusOK {
		return IPQualitySource{}, fmt.Errorf("ipinfo.io: status %d", status)
	}
	var raw struct {
		IP       string `json:"ip"`
		City     string `json:"city"`
		Region   string `json:"region"`
		Country  string `json:"country"`
		Loc      string `json:"loc"`
		Org      string `json:"org"`
		Timezone string `json:"timezone"`
	}
	if err := json.Unmarshal(body, &raw); err != nil {
		return IPQualitySource{}, err
	}
	if raw.IP == "" {
		return IPQualitySource{}, fmt.Errorf("ipinfo.io: empty response")
	}
	latitude, longitude := 0.0, 0.0
	if parts := strings.Split(raw.Loc, ","); len(parts) == 2 {
		fmt.Sscanf(parts[0], "%f", &latitude)
		fmt.Sscanf(parts[1], "%f", &longitude)
	}
	return IPQualitySource{
		Name:      "ipinfo.io",
		Country:   raw.Country,
		Region:    raw.Region,
		City:      raw.City,
		ASN:       raw.Org,
		ISP:       raw.Org,
		Latitude:  latitude,
		Longitude: longitude,
	}, nil
}

// QueryIPQuality queries aggregated IP intelligence for the given IP.
// An empty ip resolves the current exit IP through the tunnel first.
func QueryIPQuality(ip string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), ipQualityTimeout)
	defer cancel()

	target := strings.TrimSpace(ip)
	if target == "" {
		target = resolveExitIP(ctx)
		if target == "" {
			return "", fmt.Errorf("failed to resolve exit ip")
		}
	}

	var (
		wg                        sync.WaitGroup
		scam                      scamalyticsResult
		check                     proxyCheckResult
		api                       ipApiResult
		who                       IPQualitySource
		co                        IPQualitySource
		info                      IPQualitySource
		scamErr, checkErr, apiErr error
		whoErr, coErr, infoErr    error
	)

	wg.Add(6)
	go func() {
		defer wg.Done()
		scam, scamErr = fetchScamalytics(ctx, target)
	}()
	go func() {
		defer wg.Done()
		check, checkErr = fetchProxyCheck(ctx, target)
	}()
	go func() {
		defer wg.Done()
		api, apiErr = fetchIPApi(ctx, target)
	}()
	go func() {
		defer wg.Done()
		who, whoErr = fetchIPWhoIs(ctx, target)
	}()
	go func() {
		defer wg.Done()
		co, coErr = fetchIPAPICo(ctx, target)
	}()
	go func() {
		defer wg.Done()
		info, infoErr = fetchIPInfoIO(ctx, target)
	}()
	wg.Wait()

	// at least one source must succeed
	if scamErr != nil && checkErr != nil && apiErr != nil && whoErr != nil && coErr != nil && infoErr != nil {
		errs := []string{scamErr.Error(), checkErr.Error(), apiErr.Error(), whoErr.Error(), coErr.Error(), infoErr.Error()}
		return "", fmt.Errorf("all sources failed: %s", strings.Join(errs, "; "))
	}

	quality := IPQuality{IP: target}

	if scamErr == nil {
		quality.ScamalyticsScore = scam.score
		quality.ScamalyticsRisk = scam.risk
		quality.Sources = append(quality.Sources, IPQualitySource{Name: "Scamalytics", Risk: scam.risk})
	}

	if checkErr == nil {
		quality.ProxyCheckRisk = check.risk
		quality.ProxyCheckProxy = check.proxy
		quality.ProxyCheckType = check.typ
		quality.Sources = append(quality.Sources, IPQualitySource{
			Name:  "ProxyCheck",
			Risk:  check.risk,
			Proxy: check.proxy,
			Type:  check.typ,
		})
	}

	if apiErr == nil {
		quality.IPApiProxy = api.proxy
		quality.IPApiHosting = api.hosting
		quality.IPApiMobile = api.mobile
		quality.ASN = api.as
		quality.IPApiISP = api.isp
		quality.Sources = append(quality.Sources, IPQualitySource{
			Name:    "IP-API",
			ISP:     api.isp,
			ASN:     api.as,
			Proxy:   api.proxy,
			Hosting: api.hosting,
		})
	}
	if whoErr == nil {
		quality.Sources = append(quality.Sources, who)
	}
	if coErr == nil {
		quality.Sources = append(quality.Sources, co)
	}
	if infoErr == nil {
		quality.Sources = append(quality.Sources, info)
	}

	data, err := json.Marshal(quality)
	if err != nil {
		return "", err
	}

	return string(data), nil
}

// TestLatency measures HTTP round-trip latency for the given URL through the tunnel.
// Returns latency in milliseconds; a negative value indicates failure.
func TestLatency(url string) int64 {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	start := time.Now()

	response, err := clashHttp.HttpRequest(ctx, url, http.MethodGet,
		http.Header{"User-Agent": {browserUserAgent}}, nil)
	if err != nil {
		return -1
	}
	defer response.Body.Close()
	io.Copy(io.Discard, io.LimitReader(response.Body, 4096))

	return time.Since(start).Milliseconds()
}

var serverLocationCache sync.Map
var serverIPCache sync.Map

func resolveServerIP(ctx context.Context, rawURL string) string {
	parsed, err := url.Parse(rawURL)
	if err != nil {
		return ""
	}
	host := parsed.Hostname()
	if host == "" {
		return ""
	}
	if ip := net.ParseIP(host); ip != nil {
		return ip.String()
	}
	if cached, ok := serverIPCache.Load(host); ok {
		return cached.(string)
	}
	resolvers := []string{
		"https://dns.google/resolve?name=" + url.QueryEscape(host) + "&type=A",
		"https://cloudflare-dns.com/dns-query?name=" + url.QueryEscape(host) + "&type=A",
	}
	for _, endpoint := range resolvers {
		body, status, err := httpGet(ctx, endpoint)
		if err != nil || status != http.StatusOK {
			continue
		}
		var response struct {
			Answer []struct {
				Type int    `json:"type"`
				Data string `json:"data"`
			} `json:"Answer"`
		}
		if err := json.Unmarshal(body, &response); err != nil {
			continue
		}
		for _, answer := range response.Answer {
			if answer.Type == 1 && net.ParseIP(answer.Data) != nil {
				serverIPCache.Store(host, answer.Data)
				return answer.Data
			}
		}
	}
	if addresses, err := net.DefaultResolver.LookupIPAddr(ctx, host); err == nil {
		for _, address := range addresses {
			if ipv4 := address.IP.To4(); ipv4 != nil && !(ipv4[0] == 198 && (ipv4[1] == 18 || ipv4[1] == 19)) {
				serverIPCache.Store(host, address.IP.String())
				return address.IP.String()
			}
		}
	}
	return ""
}

func locateServerIP(ctx context.Context, ip string) IPQualitySource {
	if cached, ok := serverLocationCache.Load(ip); ok {
		return cached.(IPQualitySource)
	}
	source, err := fetchIPWhoIs(ctx, ip)
	if err != nil {
		source, err = fetchIPAPICo(ctx, ip)
	}
	if err != nil {
		source, err = fetchIPInfoIO(ctx, ip)
	}
	if err == nil {
		serverLocationCache.Store(ip, source)
	}
	return source
}

// ProbeResult describes the HTTP status and latency of a probed URL.
type ProbeResult struct {
	Code      int     `json:"code"`    // HTTP status code; -1 on connection error
	Latency   int64   `json:"latency"` // round-trip in ms; -1 on failure
	ServerIP  string  `json:"serverIp,omitempty"`
	Country   string  `json:"country,omitempty"`
	City      string  `json:"city,omitempty"`
	Latitude  float64 `json:"latitude,omitempty"`
	Longitude float64 `json:"longitude,omitempty"`
}

// ProbeURL performs an HTTP GET through the tunnel and reports the final
// status code (after redirects) with the round-trip latency.
func ProbeURL(rawURL string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	result := ProbeResult{Code: -1, Latency: -1}
	result.ServerIP = resolveServerIP(ctx, rawURL)
	populateLocation := func() {
		if result.ServerIP == "" {
			return
		}
		if location := locateServerIP(ctx, result.ServerIP); location.Name != "" {
			result.Country = location.Country
			result.City = location.City
			result.Latitude = location.Latitude
			result.Longitude = location.Longitude
		}
	}

	start := time.Now()
	response, err := clashHttp.HttpRequest(ctx, rawURL, http.MethodGet,
		http.Header{"User-Agent": {browserUserAgent}}, nil)
	if err != nil {
		populateLocation()
		data, _ := json.Marshal(result)
		return string(data), nil
	}

	defer response.Body.Close()
	io.Copy(io.Discard, io.LimitReader(response.Body, 4096))

	result.Code = response.StatusCode
	result.Latency = time.Since(start).Milliseconds()
	populateLocation()

	data, err := json.Marshal(result)
	if err != nil {
		return "", err
	}

	return string(data), nil
}
