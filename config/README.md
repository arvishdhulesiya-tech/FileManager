# Configuration

FileManager supports configuration through **Java system properties** (`-D...`) or **environment variables**.

Java system properties take precedence when both are present.

- Windows/Tomcat GUI: see `windows-tomcat-java-options.example.txt`.
- Linux/Tomcat scripts: see `linux-setenv.sh.example`.
- Generic environment template: see `filemanager.env.example`.

Never commit a real password or other secret to GitHub.
