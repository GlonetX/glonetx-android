package config

import (
	"net"
	"reflect"
	"testing"

	mihomoconfig "github.com/metacubex/mihomo/config"
)

func TestPatchNetworkCapabilities(t *testing.T) {
	cfg := mihomoconfig.DefaultRawConfig()
	cfg.Port = 7890
	cfg.SocksPort = 7891
	cfg.RedirPort = 7892
	cfg.TProxyPort = 7893
	cfg.MixedPort = 7894
	cfg.AllowLan = true
	cfg.BindAddress = "0.0.0.0"
	cfg.Interface = "wlan0"
	cfg.RoutingMark = 6666
	cfg.DNS.Enable = true
	cfg.DNS.Listen = "0.0.0.0:5353"
	cfg.Listeners = []map[string]any{
		{"name": "mixed", "type": "mixed", "port": 7895, "listen": "0.0.0.0"},
		{"name": "socks", "type": "socks", "port": 7896, "listen": "0.0.0.0"},
		{"name": "http", "type": "http", "port": 7897, "listen": "0.0.0.0"},
		{"name": "tunnel", "type": "tunnel", "port": 7898, "listen": "0.0.0.0"},
		{"name": "redir", "type": "redir", "port": 7899, "listen": "0.0.0.0"},
	}

	proxies := []map[string]any{
		{"name": "proxy", "type": "socks5", "server": "example.com", "port": 1080},
	}
	rules := []string{"DOMAIN,example.com,proxy", "MATCH,DIRECT"}
	cfg.Proxy = proxies
	cfg.Rule = rules

	if err := patchGeneral(cfg, ""); err != nil {
		t.Fatalf("patchGeneral() error = %v", err)
	}
	if err := patchListeners(cfg, ""); err != nil {
		t.Fatalf("patchListeners() error = %v", err)
	}

	if cfg.Port != 0 || cfg.SocksPort != 0 || cfg.RedirPort != 0 || cfg.TProxyPort != 0 || cfg.MixedPort != 0 {
		t.Fatalf("listener ports were not cleared: port=%d socks=%d redir=%d tproxy=%d mixed=%d",
			cfg.Port, cfg.SocksPort, cfg.RedirPort, cfg.TProxyPort, cfg.MixedPort)
	}
	if cfg.AllowLan {
		t.Fatal("allow-lan was not cleared")
	}
	if cfg.BindAddress != "127.0.0.1" {
		t.Fatalf("bind-address = %q, want loopback", cfg.BindAddress)
	}
	if cfg.Interface != "" || cfg.RoutingMark != 0 {
		t.Fatalf("interface/routing-mark were not cleared: interface=%q routing-mark=%d", cfg.Interface, cfg.RoutingMark)
	}
	if len(cfg.Listeners) != 0 {
		t.Fatalf("listeners were not cleared: %#v", cfg.Listeners)
	}

	host, _, err := net.SplitHostPort(cfg.DNS.Listen)
	if err != nil {
		t.Fatalf("dns listen %q is not a host:port address: %v", cfg.DNS.Listen, err)
	}
	if ip := net.ParseIP(host); ip == nil || !ip.IsLoopback() {
		t.Fatalf("dns listen host = %q, want loopback", host)
	}

	if !reflect.DeepEqual(cfg.Proxy, proxies) {
		t.Fatalf("proxies changed: got %#v, want %#v", cfg.Proxy, proxies)
	}
	if !reflect.DeepEqual(cfg.Rule, rules) {
		t.Fatalf("rules changed: got %#v, want %#v", cfg.Rule, rules)
	}
}

func TestPatchOverrideInjectsTailscaleProxy(t *testing.T) {
	previous := sessionOverride
	defer func() { sessionOverride = previous }()
	sessionOverride = `{"tailscale":{"enabled":true,"name":"ts-test"}}`

	cfg := &mihomoconfig.RawConfig{}
	if err := patchOverride(cfg, ""); err != nil {
		t.Fatalf("patchOverride() error = %v", err)
	}
	if len(cfg.Proxy) != 1 {
		t.Fatalf("proxy count = %d, want 1", len(cfg.Proxy))
	}
	if cfg.Proxy[0]["type"] != "tailscale" {
		t.Fatalf("proxy type = %v, want tailscale", cfg.Proxy[0]["type"])
	}
	if cfg.Proxy[0]["name"] != "ts-test" {
		t.Fatalf("proxy name = %v, want ts-test", cfg.Proxy[0]["name"])
	}
	if cfg.Proxy[0]["state-dir"] != "tailscale/ts-test" {
		t.Fatalf("state-dir = %v, want tailscale/ts-test", cfg.Proxy[0]["state-dir"])
	}
}

func TestPatchOverrideSkipsDisabledTailscale(t *testing.T) {
	previous := sessionOverride
	defer func() { sessionOverride = previous }()
	sessionOverride = `{"tailscale":{"enabled":false,"name":"ts-test"}}`

	cfg := &mihomoconfig.RawConfig{}
	if err := patchOverride(cfg, ""); err != nil {
		t.Fatalf("patchOverride() error = %v", err)
	}
	if len(cfg.Proxy) != 0 {
		t.Fatalf("proxy count = %d, want 0", len(cfg.Proxy))
	}
}
