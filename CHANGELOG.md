# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

...

## 0.3.6 - 2024-10-22

### Enhanced

- Old ZIP indices (w/o the compression method), defaults to 8 (deflated)

## 0.3.5 - 2024-10-21

### Enhanced

- ZIP index now includes a third value per entry - the compression method

### Added

- Convenience variant of `zip-entry-meta->uncompressed-bytes` for updated index

## 0.3.4 - 2024-10-21

### Added
- ZIP entry compression method (store, deflate etc.) to zip metadata,
  ref.: https://en.wikipedia.org/wiki/ZIP_(file_format)
- fn `zip-entry-meta->uncompressed-bytes` that wraps functions for different
  compression methods. Currently 0 (none) and 8 (deflate) is supported.

## 0.3.3 - 2024-10-10

Fix issue when traversing huge (ZIP64) files to the end.

## 0.3.2 - 2024-10-09

Support for direct unzipping of individual entries in a zip file by using
compressed entry data offset and compressed length.

## 0.2.0 - 2024-07-17

### Added
- fn `targz-entries` that return a lazy seq of entry data.

## 0.1.0 - 2024-06-11
### Added
- Initial public release - **EVERYTHING** may change!

[Unreleased]: https://github.com/luposlip/clarch/compare/0.1.1...HEAD
[0.1.0]: https://github.com/luposlip/clarch/compare/HEAD...0.1.0
