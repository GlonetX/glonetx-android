package config

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"github.com/dlclark/regexp2"

	"cfa/native/common"

	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
)

var processors = []processor{
	patchExternalController, // only allow external controllers from explicit overrides
	patchOverride,
	patchRuntimeSettings,
	patchGeneral,
	patchProfile,
	patchDns,
	patchTun,
	patchListeners,
	patchProviders,
	validConfig,
}

type processor func(cfg *config.RawConfig, profileDir string) error

func patchOverride(cfg *config.RawConfig, _ string) error {
	var tailscaleProxy map[string]any
	for _, slot := range []OverrideSlot{OverrideSlotPersist, OverrideSlotSession} {
		raw := ReadOverride(slot)
		if err := json.NewDecoder(strings.NewReader(raw)).Decode(cfg); err != nil {
			log.Warnln("Apply override: %s", err.Error())
		}

		var envelope struct {
			Tailscale json.RawMessage `json:"tailscale"`
		}
		if err := json.Unmarshal([]byte(raw), &envelope); err != nil || len(envelope.Tailscale) == 0 {
			continue
		}
		if string(envelope.Tailscale) == "null" {
			tailscaleProxy = nil
			continue
		}
		var proxy map[string]any
		if err := json.Unmarshal(envelope.Tailscale, &proxy); err != nil {
			continue
		}
		if enabled, ok := proxy["enabled"].(bool); ok && !enabled {
			tailscaleProxy = nil
			continue
		}
		delete(proxy, "enabled")
		proxy["type"] = "tailscale"
		if name, _ := proxy["name"].(string); name == "" {
			proxy["name"] = "tailscale"
		}
		if stateDir, _ := proxy["state-dir"].(string); stateDir == "" {
			if name, _ := proxy["name"].(string); name != "" {
				proxy["state-dir"] = "tailscale/" + strings.NewReplacer("/", "_", "\\", "_").Replace(name)
			}
		}
		tailscaleProxy = proxy
	}
	if tailscaleProxy != nil {
		if name, _ := tailscaleProxy["name"].(string); name != "" {
			proxies := make([]map[string]any, 0, len(cfg.Proxy)+1)
			for _, proxy := range cfg.Proxy {
				if proxy["name"] != name {
					proxies = append(proxies, proxy)
				}
			}
			cfg.Proxy = proxies
		}
		cfg.Proxy = append(cfg.Proxy, tailscaleProxy)
		if name, _ := tailscaleProxy["name"].(string); name != "" {
			route := fmt.Sprintf("IP-CIDR,100.64.0.0/10,%s,no-resolve", name)
			rules := make([]string, 0, len(cfg.Rule)+1)
			rules = append(rules, route)
			for _, rule := range cfg.Rule {
				if rule != route {
					rules = append(rules, rule)
				}
			}
			cfg.Rule = rules
		}
	}

	return nil
}

func patchExternalController(cfg *config.RawConfig, _ string) error {
	cfg.ExternalController = ""
	cfg.ExternalControllerTLS = ""

	return nil
}

func patchGeneral(cfg *config.RawConfig, profileDir string) error {
	cfg.Interface = ""
	cfg.RoutingMark = 0

	// Untrusted profiles must not be able to open network listeners.
	cfg.Port = 0
	cfg.SocksPort = 0
	cfg.RedirPort = 0
	cfg.TProxyPort = 0
	cfg.MixedPort = 0
	cfg.AllowLan = false
	cfg.BindAddress = "127.0.0.1"
	cfg.DNS.Listen = "127.0.0.1:53"

	if cfg.ExternalController != "" || cfg.ExternalControllerTLS != "" {
		cfg.ExternalUI = profileDir + "/ui"
	}

	return nil
}

func patchProfile(cfg *config.RawConfig, _ string) error {
	cfg.Profile.StoreSelected = false
	// RuntimeSettings supplies the default and the explicit user choice.

	return nil
}

func patchDns(cfg *config.RawConfig, _ string) error {
	if !cfg.DNS.Enable {
		cfg.DNS = config.DefaultRawConfig().DNS
		cfg.DNS.Enable = true
		cfg.DNS.NameServer = defaultNameServers
		cfg.DNS.EnhancedMode = C.DNSFakeIP
		cfg.DNS.FakeIPRange = defaultFakeIPRange
		cfg.DNS.FakeIPFilter = defaultFakeIPFilter

		cfg.ClashForAndroid.AppendSystemDNS = true
	}

	if cfg.ClashForAndroid.AppendSystemDNS {
		cfg.DNS.NameServer = append(cfg.DNS.NameServer, "system://")
	}

	return nil
}

func patchTun(cfg *config.RawConfig, _ string) error {
	cfg.Tun.Enable = false
	cfg.Tun.AutoRoute = false
	cfg.Tun.AutoDetectInterface = false
	return nil
}

func patchListeners(cfg *config.RawConfig, _ string) error {
	// Listener definitions are network capabilities and cannot be trusted from
	// an untrusted profile, regardless of the listener type.
	cfg.Listeners = nil
	return nil
}

func patchProviders(cfg *config.RawConfig, profileDir string) error {
	forEachProviders(cfg, func(index int, total int, key string, provider map[string]any, prefix string) {
		path, _ := provider["path"].(string)
		if len(path) > 0 {
			path = common.ResolveAsRoot(path)
		} else if url, ok := provider["url"].(string); ok {
			path = prefix + "/" + utils.MakeHash([]byte(url)).String() // same as C.GetPathByHash
		} else {
			return // both path and url are empty, maybe inline provider
		}
		provider["path"] = profileDir + "/providers/" + path
	})

	return nil
}

func validConfig(cfg *config.RawConfig, _ string) error {
	if len(cfg.Proxy) == 0 && len(cfg.ProxyProvider) == 0 {
		return errors.New("profile does not contain `proxies` or `proxy-providers`")
	}

	if _, err := regexp2.Compile(cfg.ClashForAndroid.UiSubtitlePattern, 0); err != nil {
		return fmt.Errorf("compile ui-subtitle-pattern: %s", err.Error())
	}

	return nil
}

func process(cfg *config.RawConfig, profileDir string) error {
	for _, p := range processors {
		if err := p(cfg, profileDir); err != nil {
			return err
		}
	}

	return nil
}
