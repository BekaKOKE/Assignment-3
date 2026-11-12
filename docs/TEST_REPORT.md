# Test Cases and Results

Generated from the real JUnit/Surefire output of `mvn test` on 2026-11-12 20:05.

**Result: ALL PASSED** - 165 test executions, 0 failed, 0 skipped.

## How the tests are organised

* A *test case* (TC-xx) is one scenario. Scenarios that exercise the Bridge are **parameterised**:
  they run once per storage implementation (`MEMORY`, `LOCAL`, `DATABASE`; `LOCAL` and `DATABASE` for the
  end-to-end tests), which is why the *Runs* column is often 2 or 3. The same assertions must hold for every
  implementation - that is the proof that the abstraction does not depend on the storage.
* Every test uses a fresh temporary directory (JUnit `@TempDir`) and a fresh SQLite file, so tests are independent.
* The Proxy tests wrap the real subject in a recording spy, so they can assert that **denied requests never reach the
  real subject** (TC-P04) and granted ones do (TC-P05).

Run them yourself: `mvn test` (details in README).

## Test cases

| ID | What is verified | Test class | Runs | Result |
|----|------------------|------------|-----:|--------|
| TC-B01 | BRIDGE: switching the storage implementation keeps all content readable | FileManagerBridgeTest | 3 | PASS |
| TC-B02 | BRIDGE: a failing target backend leaves the system on the old backend, data intact | FileManagerBridgeTest | 1 | PASS |
| TC-B03 | BRIDGE: the abstraction depends only on the StorageBackend interface | FileManagerBridgeTest | 1 | PASS |
| TC-D01 | create a folder and list folders | FolderTest | 3 | PASS |
| TC-D02 | invalid or duplicate folder names are rejected | FolderTest | 3 | PASS |
| TC-D03 | move a file into a folder, see it in the contents, and move it back to the root | FolderTest | 3 | PASS |
| TC-D04 | moving into a missing folder or another owner's folder is rejected | FolderTest | 3 | PASS |
| TC-D05 | POLICY: a non-empty folder cannot be deleted; its files are untouched | FolderTest | 3 | PASS |
| TC-D06 | an empty folder can be deleted (also after its files were moved out) | FolderTest | 3 | PASS |
| TC-D07 | deleting a file inside a folder empties the folder, so it can then be deleted | FolderTest | 3 | PASS |
| TC-E01 | required scenario: create -> store -> share -> second user reads through the proxy | ScenarioTest | 2 | PASS |
| TC-E02 | metadata, content and shares persist across an application restart | ScenarioTest | 2 | PASS |
| TC-E03 | BRIDGE: storage is swapped at runtime and by restart option; same code, same results | ScenarioTest | 2 | PASS |
| TC-E04 | the old backend is cleaned up after a swap (no duplicated content) | ScenarioTest | 2 | PASS |
| TC-E05 | deleting a file removes it for every user and from storage | ScenarioTest | 2 | PASS |
| TC-F01 | create stores content via the backend and returns correct metadata | FileManagerBridgeTest | 3 | PASS |
| TC-F02 | list shows all files, search is case-insensitive and partial | FileManagerBridgeTest | 3 | PASS |
| TC-F03 | rename changes the name only; content and metadata are kept | FileManagerBridgeTest | 3 | PASS |
| TC-F04 | delete removes metadata and content | FileManagerBridgeTest | 3 | PASS |
| TC-F05 | duplicate name+type for one owner is rejected, other owner or other type is fine | FileManagerBridgeTest | 3 | PASS |
| TC-F06 | invalid input is rejected and leaves no half-created file behind | FileManagerBridgeTest | 3 | PASS |
| TC-M01 | file metadata round-trips through SQLite, including shares and creation date | MetadataRepositoryTest | 1 | PASS |
| TC-M02 | saving again updates name, folder and shares | MetadataRepositoryTest | 1 | PASS |
| TC-M03 | deleting a file also removes its shares | MetadataRepositoryTest | 1 | PASS |
| TC-M04 | the database refuses a file that points at a missing folder (integrity) | MetadataRepositoryTest | 1 | PASS |
| TC-M05 | folders round-trip and can be deleted | MetadataRepositoryTest | 1 | PASS |
| TC-P01 | OWNER can read, rename, share and delete | ProtectionProxyTest | 3 | PASS |
| TC-P02 | VIEWER can read a shared file | ProtectionProxyTest | 3 | PASS |
| TC-P03 | VIEWER cannot rename, delete, share or move; the file stays untouched | ProtectionProxyTest | 3 | PASS |
| TC-P04 | denied requests never reach the real subject | ProtectionProxyTest | 3 | PASS |
| TC-P05 | granted requests ARE forwarded to the real subject | ProtectionProxyTest | 3 | PASS |
| TC-P06 | a user with no access cannot read, see or search the file | ProtectionProxyTest | 3 | PASS |
| TC-P07 | permissions come from stored data: sharing changes what the proxy allows | ProtectionProxyTest | 3 | PASS |
| TC-P08 | the same user is OWNER of own files and VIEWER of shared ones | ProtectionProxyTest | 1 | PASS |
| TC-P09 | unknown users and null users are denied everything | ProtectionProxyTest | 1 | PASS |
| TC-P10 | every decision is written to the audit log (GRANTED and DENIED) | ProtectionProxyTest | 1 | PASS |
| TC-P11 | the denial message names the user, role, operation and file | ProtectionProxyTest | 1 | PASS |
| TC-P12 | sharing with an unknown user or with the owner is rejected | ProtectionProxyTest | 1 | PASS |
| TC-P13 | only the folder owner can delete a folder or move files into it | ProtectionProxyTest | 3 | PASS |
| TC-P14 | a viewer sees a folder only through files shared with them | ProtectionProxyTest | 3 | PASS |
| TC-R01 | role is derived from the stored owner and share list | PermissionCheckerTest | 1 | PASS |
| TC-R02 | OWNER has every permission | PermissionCheckerTest | 1 | PASS |
| TC-R03 | VIEWER may only READ | PermissionCheckerTest | 1 | PASS |
| TC-R04 | a user with no role has no permission | PermissionCheckerTest | 1 | PASS |
| TC-S01 | write then read returns the same bytes (incl. unicode and binary) | StorageBackendTest | 3 | PASS |
| TC-S02 | writing an existing key overwrites it | StorageBackendTest | 3 | PASS |
| TC-S03 | reading a missing key throws NotFoundException | StorageBackendTest | 3 | PASS |
| TC-S04 | exists / delete; deleting a missing key is not an error | StorageBackendTest | 3 | PASS |
| TC-S05 | empty content can be stored | StorageBackendTest | 3 | PASS |
| TC-S06 | local storage rejects ids that could escape the storage directory | StorageBackendTest | 1 | PASS |
| TC-S07 | local storage keeps the content as a real file on disk | StorageBackendTest | 1 | PASS |
| TC-V01 | valid names are accepted | ValidatorTest | 5 | PASS |
| TC-V02 | names are trimmed | ValidatorTest | 1 | PASS |
| TC-V03 | empty, whitespace, path and wildcard names are rejected | ValidatorTest | 17 | PASS |
| TC-V04 | name longer than the limit is rejected, name at the limit is accepted | ValidatorTest | 1 | PASS |
| TC-V05 | content: empty and unicode allowed, null and oversize rejected | ValidatorTest | 1 | PASS |
| TC-V06 | known file types parse regardless of case, dot and spaces | ValidatorTest | 8 | PASS |
| TC-V07 | unknown or empty file types are rejected | ValidatorTest | 6 | PASS |
| TC-W01 | required scenario over HTTP: create, share, second user reads, denied actions change nothing | WebApiTest | 1 | PASS |
| TC-W02 | search works through the proxy: case-insensitive for the owner, nothing leaks to others | WebApiTest | 1 | PASS |
| TC-W03 | missing login is 401; an unknown user is refused by the proxy (403) and recorded safely | WebApiTest | 1 | PASS |
| TC-W04 | only localhost / 127.0.0.1 Host headers are accepted (DNS-rebinding guard) | WebApiTest | 1 | PASS |
| TC-W05 | a state-changing request from a foreign Origin is refused and changes nothing | WebApiTest | 1 | PASS |
| TC-W06 | an oversized request body is rejected with 413 before it is read | WebApiTest | 1 | PASS |
| TC-W07 | the page is served with a strict CSP; unknown paths and wrong methods are refused | WebApiTest | 1 | PASS |
| TC-W08 | validation, not-found and conflict errors map to 400, 404 and 409 | WebApiTest | 1 | PASS |
| TC-W09 | BRIDGE over HTTP: switching storage keeps content readable and is logged; strangers cannot switch | WebApiTest | 1 | PASS |
| TC-W10 | folders over HTTP: non-empty folder cannot be deleted; only its owner may delete it | WebApiTest | 1 | PASS |
| TC-W11 | JSON writer: structure, null and numbers | JsonTest | 1 | PASS |
| TC-W12 | JSON writer escapes quotes, control characters and markup characters | JsonTest | 1 | PASS |
| TC-W13 | JSON writer refuses types it cannot represent | JsonTest | 1 | PASS |
| TC-W14 | non-ASCII text (Cyrillic, accents, emoji) survives a round trip over HTTP unchanged | WebApiTest | 1 | PASS |

## Coverage of the assignment requirements

| Requirement | Test cases |
|-------------|------------|
| Create / list / search / rename / delete / info for files | TC-F01 - TC-F04, TC-E01, TC-E05 |
| Folder create / list / move / delete, documented policy for non-empty folders | TC-D01 - TC-D07, TC-P13, TC-P14 |
| Owner permissions (read, rename, delete, share) | TC-P01, TC-R01, TC-R02 |
| Viewer permissions (read shared files only; no rename, delete, share) | TC-P02, TC-P03, TC-R03, TC-P08 |
| Bridge: two or more storage implementations, one abstraction | TC-S01 - TC-S07, TC-F01 - TC-F06 (parameterised) |
| Bridge: storage can be changed without modifying the abstraction | TC-B01, TC-B02, TC-B03, TC-E03, TC-E04 |
| Proxy: checks permission, denies unauthorised requests, uses stored permission data | TC-P03 - TC-P07, TC-P09 - TC-P12 |
| Proxy: client goes through the proxy, not the real subject | TC-P04, TC-P05, TC-E01 |
| Required scenario (create, store, share, second user reads through proxy) | TC-E01 |
| Persistent metadata and content | TC-M01 - TC-M05, TC-E02 |
| Input validation and error handling | TC-V01 - TC-V07, TC-F05, TC-F06, TC-S03, TC-S06, TC-P12 |
| Browser interface: same scenario over HTTP, denied calls answer 403 and change nothing | TC-W01, TC-W02, TC-W03, TC-W09, TC-W10 |
| Browser interface: locked down to localhost (Host / Origin checks, body limit, CSP, no path lookup) | TC-W04 - TC-W07 |
| Browser interface: error mapping, Unicode round trip, safe JSON | TC-W08, TC-W11 - TC-W14 |

## Manual tests of the applications

* Console: the scripted demo (`java -jar target/file-manager.jar --demo`) walks through the required scenario and
  prints every proxy decision. Its output is reproduced in README.md.
* Browser: `java -jar target/file-manager.jar --web` starts the server on port 8421; the same scenario was run
  against the live server with `curl` (kuanysh denied, beksultan shares, ruslan reads, ruslan denied rename and delete, a foreign
  `Host` header refused, every decision visible in `/api/log`). Page script syntax checked with `node --check`.

## Sanity check that the tests can fail

To make sure the permission tests are not vacuous, `Role.VIEWER` was temporarily changed to also allow `RENAME`.
The suite then failed (`PermissionCheckerTest.viewerPermissions` and 8 `ProtectionProxyTest` executions). The change
was reverted and the suite is green again.
