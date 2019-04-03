# Changelog

## [Unreleased]
Need more tests and fix `buildMultyPolygon` non-determinism.

### Added
- Tests for `buildMultyPolygon`.
- Changelog.

### Changed
- Started a new version.

### Fixed
- Readme file.

### Removed
- ???

### Deprecated
- ???

## [1.9.1] - 2018-12-30
Changes in this release was induced by other projects: we are require full control 
over the build process and smooth integration of this project functionality into our Spark apps.

### Added
- Subproject: external-sorting library from https://github.com/kiselev-dv/ExternalSorting.
- Subproject: osm-doc-java library from https://github.com/kiselev-dv/osm-doc-java.
- SBT multiproject (build.sbt).

### Changed 
- Package names and dependencies.
- Project dir structure.

### Fixed
- Module names.

[Unreleased]: https://github.com/vasnake/gazetteer/compare/v1.9.1...vasnake:osm-etl-lib
[1.9.1]: https://github.com/vasnake/gazetteer/compare/Gazetteer-1.9...v1.9.1
