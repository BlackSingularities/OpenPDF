; Instalator OpenDocument dla Windows (jeden plik setup.exe, bez wersji przenośnej).
; Budowany w GitHub Actions: iscc /DAppVersion=x.y.z /DSourceDir=<obraz aplikacji> OpenDocument.iss

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\build\compose\binaries\main\app\OpenDocument"
#endif

[Setup]
AppId={{8B0E3C52-6B7B-4D71-9F59-3C1A0F2D7E41}
AppName=OpenDocument
AppVersion={#AppVersion}
AppVerName=OpenDocument {#AppVersion}
AppPublisher=redzikowski.com
AppPublisherURL=https://redzikowski.com
AppSupportURL=https://redzikowski.com
DefaultDirName={autopf}\OpenDocument
DefaultGroupName=OpenDocument
DisableProgramGroupPage=yes
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputBaseFilename=OpenDocument-{#AppVersion}-setup-x64
OutputDir=output
SetupIconFile=icon.ico
UninstallDisplayIcon={app}\OpenDocument.exe
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ShowLanguageDialog=auto
CloseApplications=yes
RestartApplications=no
ChangesAssociations=yes

[Languages]
Name: "polish"; MessagesFile: "compiler:Languages\Polish.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[CustomMessages]
polish.Assoc=Otwieraj dokumenty w OpenDocument (PDF, DjVu, EPUB, FB2, MOBI, CBZ)
english.Assoc=Open documents with OpenDocument (PDF, DjVu, EPUB, FB2, MOBI, CBZ)
polish.Launch=Uruchom OpenDocument
english.Launch=Launch OpenDocument

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "assoc"; Description: "{cm:Assoc}"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\OpenDocument"; Filename: "{app}\OpenDocument.exe"
Name: "{autodesktop}\OpenDocument"; Filename: "{app}\OpenDocument.exe"; Tasks: desktopicon

[Registry]
Root: HKA; Subkey: "Software\Classes\OpenDocument.File"; ValueType: string; ValueData: "Dokument"; Flags: uninsdeletekey; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\OpenDocument.File\DefaultIcon"; ValueType: string; ValueData: "{app}\OpenDocument.exe,0"; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\OpenDocument.File\shell\open\command"; ValueType: string; ValueData: """{app}\OpenDocument.exe"" ""%1"""; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\Applications\OpenDocument.exe\SupportedTypes"; ValueType: string; ValueName: ".pdf"; ValueData: ""; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\.pdf\OpenWithProgids"; ValueType: string; ValueName: "OpenDocument.File"; ValueData: ""; Flags: uninsdeletevalue; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\.djvu\OpenWithProgids"; ValueType: string; ValueName: "OpenDocument.File"; ValueData: ""; Flags: uninsdeletevalue; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\.djv\OpenWithProgids"; ValueType: string; ValueName: "OpenDocument.File"; ValueData: ""; Flags: uninsdeletevalue; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\.epub\OpenWithProgids"; ValueType: string; ValueName: "OpenDocument.File"; ValueData: ""; Flags: uninsdeletevalue; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\.fb2\OpenWithProgids"; ValueType: string; ValueName: "OpenDocument.File"; ValueData: ""; Flags: uninsdeletevalue; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\.mobi\OpenWithProgids"; ValueType: string; ValueName: "OpenDocument.File"; ValueData: ""; Flags: uninsdeletevalue; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\.azw3\OpenWithProgids"; ValueType: string; ValueName: "OpenDocument.File"; ValueData: ""; Flags: uninsdeletevalue; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\.cbz\OpenWithProgids"; ValueType: string; ValueName: "OpenDocument.File"; ValueData: ""; Flags: uninsdeletevalue; Tasks: assoc

[Run]
Filename: "{app}\OpenDocument.exe"; Description: "{cm:Launch}"; Flags: nowait postinstall skipifsilent
; Po cichej aktualizacji z poziomu programu – ponowne uruchomienie.
Filename: "{app}\OpenDocument.exe"; Flags: nowait skipifnotsilent

[UninstallDelete]
Type: filesandordirs; Name: "{localappdata}\OpenDocument\cache"
Type: filesandordirs; Name: "{localappdata}\OpenDocument\thumbs"
