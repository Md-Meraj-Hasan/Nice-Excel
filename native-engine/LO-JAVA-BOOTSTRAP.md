# LibreOffice Android Java bootstrap sources

This branch adds the official LibreOffice Android Bootstrap Java wrappers to the NiceExcel app source tree.

## Source pin

The four Java files under `app/src/main/java/org/libreoffice/kit/` are copied unchanged from the LibreOffice core revision:

`6e0b39149b549bd05065d451006c4d060589891d`

Files:
- `LibreOfficeKit.java` — native library loading and JNI initialization entry point
- `Office.java` — LibreOfficeKit office/document factory wrapper
- `Document.java` — document API wrapper
- `DirectBufferAllocator.java` — direct ByteBuffer helper

Each file retains its upstream MPL-2.0 header.

## Safety boundary

These classes are compiled into the Android app but are not called by the production grid or formula engine. The existing formula path and UI remain unchanged.

This is only the Java/JNI wrapper layer. A working LibreOffice runtime still requires:
1. the matching ARM64 native libraries from the same LibreOffice revision;
2. the Android runtime assets/data and fonts produced by that same build;
3. initialization on a dedicated worker/LOKit thread;
4. an XLSX open → hard-recalculate → save → reopen adapter and physical-device acceptance tests.

Do not switch production formula calculation to LibreOffice until those checks pass.
