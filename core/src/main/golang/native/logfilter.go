package main

import (
	"strings"

	"github.com/metacubex/mihomo/log"
)

func shouldForwardLogcat(payload string, level log.LogLevel) bool {
	if level >= log.Level() || strings.HasPrefix(payload, "[APP]") {
		return true
	}
	if !strings.HasPrefix(payload, "[Tailscale]") {
		return false
	}
	for _, marker := range []string{
		"To start this tsnet server",
		"Switching ipn state",
		"LocalBackend state is NeedsLogin",
		"active login:",
		"You are logged out",
		"authRoutine-success",
	} {
		if strings.Contains(payload, marker) {
			return true
		}
	}
	return false
}
