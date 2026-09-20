# License Audit: BIMO

## Overview
This document provides a comprehensive audit of the licenses used across the **BIMO** codebase, confirming license compatibility and compliance with open-source obligations.

## Primary Application License
* **License**: **GNU General Public License v3.0 (GPL-3.0)**
* **File**: `LICENSE`
* **Status**: Compliant
* **Reasoning**: BIMO incorporates code from OuterTune and InnerTune, which are both licensed under the copyleft GNU General Public License v3.0. To respect copyleft licensing obligations, BIMO and its derivative modules are distributed under GPL-3.0.

---

## Dependency & Submodule License Audit

| Component | Upstream / Author | License | Copyleft / Permissive | Compatibility with GPL-3.0 |
| :--- | :--- | :--- | :--- | :--- |
| **BIMO Application Code** | Rana | GPL-3.0 | Strong Copyleft | Native |
| **OuterTune Components** | Davide Garberi & contributors | GPL-3.0 | Strong Copyleft | Direct Base |
| **InnerTune Components** | Zion Huang & contributors | GPL-3.0 | Strong Copyleft | Upstream Base |
| **NewPipe Extractor / Innertube** | Team NewPipe | GPL-3.0 | Strong Copyleft | Compatible |
| **AndroidX / Media3 / ExoPlayer** | Google / AOSP | Apache 2.0 | Permissive | Compatible (Apache 2.0 can be combined in GPLv3) |
| **Jetpack Compose** | Google / AOSP | Apache 2.0 | Permissive | Compatible |
| **Mozilla Rhino** | Mozilla Foundation | MPL-2.0 | Weak Copyleft | Compatible (Section 3.3 GPL compatibility) |
| **OkHttp** | Square, Inc. | Apache 2.0 | Permissive | Compatible |
| **Kotlin Standard Library & Coroutines**| JetBrains s.r.o. | Apache 2.0 | Permissive | Compatible |
| **Ktor Client** | JetBrains s.r.o. | Apache 2.0 | Permissive | Compatible |
| **TagLib** | Scott Wheeler & contributors | LGPL-2.1 / MPL-1.1 | Weak Copyleft | Compatible |
| **Material Color Utilities** | Google LLC | Apache 2.0 | Permissive | Compatible |
| **Coil** | Coil Contributors | Apache 2.0 | Permissive | Compatible |

---

## Compliance Verification Checklist
- [x] All upstream copyright headers preserved in respective source files.
- [x] Full text of the GNU General Public License v3.0 maintained in `LICENSE`.
- [x] Third-party attributions documented in `THIRD-PARTY-NOTICES.md` and accessible in-app via the Open Source Licenses screen.
- [x] Apache 2.0 and MPL 2.0 compatibility with GPL-3.0 verified.
- [x] No proprietary or restrictive commercial clauses imposed on open-source code.
