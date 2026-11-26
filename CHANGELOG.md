# Changelog

All notable changes to GlonetX Android are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [12.13.0] - 2026-11-26

### Added

- Tailscale status, login, routing, and diagnostic support.
- Redesigned Compose dashboard with a global IP map by default, traffic chart, and subscription remaining-traffic/time cards.
- Proxy card/list views with column, density, sorting, flag, and selectable-group controls in a two-level options menu; card-only settings appear only in card view.
- Tabbed IP Quality screen with animated risk gauges, compact status indicators, and multi-source intelligence from Scamalytics, ProxyCheck, IP-API, ipwho.is, ipapi.co, and ipinfo.io.
- Network tools with outbound IP comparison, expanded international and domestic service probes, bundled brand logos, resolved server geolocation mapping, and DNS/network diagnostics.
- Structured kernel, network policy, configuration override, and backup settings pages.
- Encrypted backup and restore flows with WebDAV support, credential migration, and recovery journals.
- In-app update checks with signed APK checksum verification.
- Release signing certificate verification and stricter CI validation.

### Changed

- Continued migration from legacy Android Views to Jetpack Compose.
- Improved settings, profiles, connections, logs, external control, and quick-settings behavior.
- Compact bracketed IPv6 abbreviations on dashboard and quality cards.
- Updated application and core version reporting.

### Fixed

- VPN reconnect, consent, startup, and pending-settings handling.
- Restored API 23 compatibility for version-code reporting on the About screen.
- Profile import/update, file picking, logging, and subscription sharing issues.
- Backup restore recovery, data migration, and orphaned restore-data cleanup.
- Numerous dashboard, layout, localization, and stability fixes.

## [12.12.2] - 2026-10-30

### Added

- Initial Jetpack Compose interface migration.
- Encrypted backup handling and network privacy controls.

### Changed

- Updated settings and release signing infrastructure.

[Unreleased]: https://github.com/GlonetX/glonetx-android/compare/v12.12.2...HEAD
[12.13.0]: https://github.com/GlonetX/glonetx-android/compare/v12.12.2...v12.13.0
[12.12.2]: https://github.com/GlonetX/glonetx-android/releases/tag/v12.12.2
