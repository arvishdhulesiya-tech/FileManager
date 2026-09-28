# FileManager

A web-based server file manager built with **React + Java Servlets + Apache Tomcat**.

It is designed to run on a server and manage files on that same server. The application keeps the existing REST API and WAR deployment model, while making server-specific settings configurable through Java system properties or environment variables.

## Features

- Login/session authentication
- Browse configured filesystem roots
- Upload and download files
- Create, rename, copy, move and delete files/folders
- ZIP download and extraction
- Text file viewing/editing
- Optional native command execution (disabled by default)
- React frontend packaged into the Java WAR

## Requirements

- Java 11+
- Maven 3.8+
- Apache Tomcat 10.x (Jakarta Servlet 5)
- Node.js is downloaded automatically by Maven during the build; a local Node installation is not required for the normal Maven build

## Build

From the `backend` directory:

```bash
mvn clean package
```

The resulting WAR is:

```text
backend/target/FileManager.war
```

Deploy that WAR to Tomcat's `webapps` directory.

## Configuration

Do not hard-code server paths or passwords in the source code. Configure the application with environment variables or Java system properties.

See `config/filemanager.env.example`. Windows and Linux examples are also provided in the `config/` directory.

Important variables:

| Variable | Default | Description |
|---|---|---|
| `FILEMANAGER_ADMIN_USERNAME` | `admin` | Login username |
| `FILEMANAGER_ADMIN_PASSWORD` | none | Required admin password |
| `FILEMANAGER_ROOTS` | `/data` | Semicolon-separated allowed filesystem roots |
| `FILEMANAGER_READ_ONLY` | `false` | Disable write operations when `true` |
| `FILEMANAGER_ALLOW_UPLOAD` | `true` | Enable uploads |
| `FILEMANAGER_NATIVE_COMMANDS` | `false` | Enable server shell commands; keep disabled unless required |
| `FILEMANAGER_RESTRICT_BROWSING` | `true` | Enforce filesystem restrictions |
| `FILEMANAGER_RESTRICT_WHITELIST` | `true` | Only allow configured roots |
| `FILEMANAGER_COMMAND_TIMEOUT_MS` | `30000` | Native command timeout |

### Example Linux/Tomcat configuration

Set the environment before starting Tomcat:

```bash
export FILEMANAGER_ADMIN_USERNAME=admin
export FILEMANAGER_ADMIN_PASSWORD='use-a-long-random-password'
export FILEMANAGER_ROOTS='/data;/var/www/files'
```

For production, configure these through your service manager/Tomcat environment rather than putting secrets in Git.

## Security notes

- The admin password is intentionally **not** stored in source code.
- Filesystem access is restricted to configured roots by default.
- Native shell commands are disabled by default. Enabling them gives the authenticated user the ability to execute operating-system commands and should only be done in a trusted environment.
- Put Tomcat behind HTTPS in production and expose only the required public ports.
- Use a strong, unique admin password for every installation.
- Do not commit `.env` files, passwords, private keys, WAR files, or server-specific configuration.

## Configuration precedence

When the same setting is supplied both ways, Java system properties (`-D...`) take precedence over environment variables. This makes the same WAR easy to run with Windows `tomcat10w.exe` or Linux service/Tomcat scripts.

## Current deployment

The application can still be deployed as a normal WAR:

```text
https://your-domain.example/FileManager
```

The React frontend uses relative API URLs, so it does not depend on a particular domain name.

## Repository layout

```text
FileManager/
├── backend/          # Java Servlet application + Maven build
├── frontend/         # React/Vite application
├── config/            # Safe configuration examples
├── .gitignore
└── README.md
```

## License

Add the license that matches how you want to distribute FileManager before publishing the repository.

## Windows and Linux deployment

FileManager is not AWS-specific. It runs on any server where a supported Java runtime and Apache Tomcat are available, including Windows and Linux servers, whether hosted on AWS, Azure, Google Cloud, another VPS, or on-premises.

### Windows (Tomcat 10 service/GUI)

If Tomcat is managed with `tomcat10w.exe`, add these Java system properties in the **Java** tab under **Java Options**, one per line:

```text
-DFILEMANAGER_ADMIN_USERNAME=admin
-DFILEMANAGER_ADMIN_PASSWORD=CHANGE_ME
-DFILEMANAGER_ROOTS=C:\\data
-DFILEMANAGER_NATIVE_COMMANDS=false
```

The same settings can also be supplied as Windows environment variables. Java system properties take precedence.

### Linux

For Tomcat started with the standard scripts, create `bin/setenv.sh`:

```bash
export FILEMANAGER_ADMIN_USERNAME=admin
export FILEMANAGER_ADMIN_PASSWORD='CHANGE_ME'
export FILEMANAGER_ROOTS='/data'
export FILEMANAGER_NATIVE_COMMANDS=false
```

Protect the file because it contains a password, and never commit it to Git.

### Folder permissions

FileManager has two separate layers of protection:

1. **FileManager allow-list:** `FILEMANAGER_ROOTS` defines which directories the application is allowed to browse and modify. Paths outside these roots are rejected even if the operating-system account could access them.
2. **Operating-system permissions:** the account running Tomcat must itself have the required read/write permissions on the configured directories.

On Windows, grant the Tomcat service account Read/Write/Modify permission on the configured folder. On Linux, use normal ownership/permissions (for example `chown`/`chmod`) so the Tomcat service account can access the directory.

Do not configure a root such as `C:\\` or `/` unless you intentionally want the application to expose the entire server filesystem. Prefer a dedicated directory such as `C:\\data` or `/data`.
