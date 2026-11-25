package main

import (
	"testing"

	"github.com/metacubex/mihomo/log"
)

func TestShouldForwardLogcatTailscaleStatus(t *testing.T) {
	previous := log.Level()
	log.SetLevel(log.ERROR)
	defer log.SetLevel(previous)

	tests := []struct {
		name    string
		payload string
		want    bool
	}{
		{"auth url", "[Tailscale](tailscale) To start this tsnet server, go to: https://login.tailscale.com/a/test", true},
		{"running", "[Tailscale](tailscale) Switching ipn state Starting -> Running", true},
		{"needs login", "[Tailscale](tailscale) LocalBackend state is NeedsLogin", true},
		{"account", "[Tailscale](tailscale) active login: user@example.com", true},
		{"logged out", "[Tailscale](tailscale) You are logged out", true},
		{"debug noise", "[Tailscale](tailscale) wgengine: Reconfig done", false},
		{"unrelated", "[DNS] resolve example.com", false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := shouldForwardLogcat(tt.payload, log.DEBUG); got != tt.want {
				t.Fatalf("shouldForwardLogcat(%q) = %v, want %v", tt.payload, got, tt.want)
			}
		})
	}
}
