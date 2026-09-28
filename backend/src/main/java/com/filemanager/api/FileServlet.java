package com.filemanager.api;

import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@WebServlet("/api/files/*")
@MultipartConfig(
        maxFileSize = -1,
        maxRequestSize = -1,
        fileSizeThreshold = 1024 * 1024
)
public class FileServlet extends HttpServlet {

    private File path(HttpServletRequest request) {

        String value = request.getParameter("path");

        if (value == null || value.trim().isEmpty()) {
            return FileService.defaultRoot();
        }

        return FileService.resolve(value);
    }

    private boolean auth(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        if (request.getSession(false) == null
                || request.getSession(false)
                .getAttribute("USERNAME") == null) {

            ApiUtil.error(
                    response,
                    401,
                    "Not authenticated"
            );

            return false;
        }

        return true;
    }

    private String body(
            HttpServletRequest request) throws IOException {

        return new String(
                request.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );
    }

    private String val(
            String body,
            String key) {

        String pattern =
                "\"" + key
                        + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"";

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern
                        .compile(pattern)
                        .matcher(body);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r");
    }

    private List<String> vals(
            String body,
            String key) {

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern
                        .compile(
                                "\"" + key
                                        + "\"\\s*:\\s*\\[(.*?)\\]",
                                java.util.regex.Pattern.DOTALL
                        )
                        .matcher(body);

        List<String> result =
                new ArrayList<>();

        if (matcher.find()) {

            java.util.regex.Matcher valueMatcher =
                    java.util.regex.Pattern
                            .compile("\"((?:\\\\.|[^\"])*)\"")
                            .matcher(matcher.group(1));

            while (valueMatcher.find()) {

                result.add(
                        valueMatcher.group(1)
                                .replace("\\\"", "\"")
                                .replace("\\\\", "\\")
                );
            }
        }

        return result;
    }

    // ============================================================
    // GET
    // ============================================================

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        if (!auth(request, response)) {
            return;
        }

        String action =
                request.getPathInfo();

        if ("/download".equals(action)) {
            download(request, response);
            return;
        }

        if ("/content".equals(action)) {
            content(request, response);
            return;
        }

        File directory =
                path(request);

        if (!directory.isDirectory()
                || !FileService.allowed(
                        directory,
                        false)) {

            ApiUtil.error(
                    response,
                    403,
                    "Directory access denied"
            );

            return;
        }

        File[] files =
                FileService.children(
                        directory,
                        request.getParameter("sort")
                );

        StringBuilder json =
                new StringBuilder();

        json.append("{");

        json.append("\"path\":\"")
                .append(
                        ApiUtil.esc(
                                directory.getAbsolutePath()
                        )
                )
                .append("\",");

        json.append("\"readOnly\":")
                .append(FileService.READ_ONLY)
                .append(",");

        json.append("\"allowUpload\":")
                .append(FileService.ALLOW_UPLOAD)
                .append(",");

        json.append("\"nativeCommands\":")
                .append(FileService.NATIVE_COMMANDS)
                .append(",");

        json.append("\"roots\":[");
        String[] roots = FileService.configuredRoots();
        for (int i = 0; i < roots.length; i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(ApiUtil.esc(new File(roots[i]).getAbsolutePath())).append("\"");
        }
        json.append("],");

        json.append("\"entries\":[");

        for (int i = 0; i < files.length; i++) {

            if (i > 0) {
                json.append(",");
            }

            json.append(
                    FileService.jsonEntry(files[i])
            );
        }

        json.append("]}");

        ApiUtil.json(
                response,
                200,
                json.toString()
        );
    }

    // ============================================================
    // POST
    // ============================================================

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        if (!auth(request, response)) {
            return;
        }

        String action =
                request.getPathInfo();

        /*
         * IMPORTANT:
         *
         * upload() throws Exception.
         * Therefore it MUST be inside this try/catch.
         */
        try {

            if ("/upload".equals(action)) {

                upload(
                        request,
                        response
                );

                return;
            }

            String requestBody =
                    body(request);

            switch (action) {

                case "/folder":

                    create(
                            request,
                            response,
                            val(requestBody, "path"),
                            val(requestBody, "name"),
                            true
                    );

                    break;

                case "/file":

                    create(
                            request,
                            response,
                            val(requestBody, "path"),
                            val(requestBody, "name"),
                            false
                    );

                    break;

                case "/rename":

                    rename(
                            response,
                            val(requestBody, "path"),
                            val(requestBody, "name")
                    );

                    break;

                case "/delete":

                    delete(
                            response,
                            vals(requestBody, "paths")
                    );

                    break;

                case "/move":

                    move(
                            response,
                            vals(requestBody, "paths"),
                            val(requestBody, "destination"),
                            false
                    );

                    break;

                case "/copy":

                    move(
                            response,
                            vals(requestBody, "paths"),
                            val(requestBody, "destination"),
                            true
                    );

                    break;

                case "/zip":

                    makeZip(
                            request,
                            response,
                            vals(requestBody, "paths")
                    );

                    break;

                case "/content":

                    saveContent(
                            response,
                            val(requestBody, "path"),
                            val(requestBody, "content"),
                            requestBody.contains(
                                    "\"backup\":true"
                            )
                    );

                    break;

                case "/unpack":

                    unpack(
                            response,
                            val(requestBody, "path")
                    );

                    break;

                default:

                    ApiUtil.error(
                            response,
                            404,
                            "Unknown file action"
                    );
            }

        } catch (Exception exception) {

            ApiUtil.error(
                    response,
                    500,
                    exception.getMessage() == null
                            ? exception.toString()
                            : exception.getMessage()
            );
        }
    }

    // ============================================================
    // CREATE FILE / FOLDER
    // ============================================================

    private void create(
            HttpServletRequest request,
            HttpServletResponse response,
            String path,
            String name,
            boolean directory)
            throws Exception {

        if (name == null
                || name.trim().isEmpty()
                || name.contains("..")) {

            throw new IOException(
                    "Invalid name"
            );
        }

        File newFile =
                new File(
                        FileService.resolve(path),
                        name
                );

        if (!FileService.allowed(
                newFile,
                true)) {

            throw new IOException(
                    "Access denied"
            );
        }

        boolean created;

        if (directory) {
            created = newFile.mkdirs();
        } else {
            created = newFile.createNewFile();
        }

        if (!created) {

            throw new IOException(
                    "Creation failed: "
                            + newFile
            );
        }

        ApiUtil.json(
                response,
                200,
                "{\"message\":\""
                        + (directory
                        ? "Directory"
                        : "File")
                        + " created\"}"
        );
    }

    // ============================================================
    // RENAME
    // ============================================================

    private void rename(
            HttpServletResponse response,
            String path,
            String name)
            throws Exception {

        File file =
                FileService.resolve(path);

        if (name == null
                || name.trim().isEmpty()
                || name.contains("..")) {

            throw new IOException(
                    "Invalid name"
            );
        }

        File newFile =
                new File(
                        file.getParentFile(),
                        name
                );

        if (!FileService.allowed(
                file,
                true)
                || !FileService.allowed(
                newFile,
                true)
                || !file.renameTo(newFile)) {

            throw new IOException(
                    "Rename failed"
            );
        }

        ApiUtil.json(
                response,
                200,
                "{\"message\":\"Renamed successfully\"}"
        );
    }

    // ============================================================
    // DELETE
    // ============================================================

    private void delete(
            HttpServletResponse response,
            List<String> paths)
            throws Exception {

        if (paths.isEmpty()) {

            throw new IOException(
                    "No files selected"
            );
        }

        for (String value : paths) {

            File file =
                    FileService.resolve(value);

            if (!FileService.allowed(
                    file,
                    true)) {

                throw new IOException(
                        "Access denied: " + file
                );
            }

            FileService.delete(file);
        }

        ApiUtil.json(
                response,
                200,
                "{\"message\":\"Deleted successfully\"}"
        );
    }

    // ============================================================
    // MOVE / COPY
    // ============================================================

    private void move(
            HttpServletResponse response,
            List<String> paths,
            String destination,
            boolean copy)
            throws Exception {

        if (paths.isEmpty()) {

            throw new IOException(
                    "No files selected"
            );
        }

        File destinationDirectory =
                FileService.resolve(destination);

        if (!destinationDirectory.isDirectory()
                || !FileService.allowed(
                destinationDirectory,
                true)) {

            throw new IOException(
                    "Destination is not allowed"
            );
        }

        for (String value : paths) {

            File source =
                    FileService.resolve(value);

            File target =
                    new File(
                            destinationDirectory,
                            source.getName()
                    );

            if (!FileService.allowed(
                    source,
                    !copy)
                    || target.exists()) {

                throw new IOException(
                        "Operation not allowed or destination exists: "
                                + target
                );
            }

            if (copy) {

                FileService.copy(
                        source,
                        target
                );

            } else if (!source.renameTo(target)) {

                throw new IOException(
                        "Move failed: " + source
                );
            }
        }

        ApiUtil.json(
                response,
                200,
                "{\"message\":\""
                        + (copy
                        ? "Copied"
                        : "Moved")
                        + " successfully\"}"
        );
    }

    // ============================================================
    // UPLOAD
    // ============================================================

    private void upload(
            HttpServletRequest request,
            HttpServletResponse response)
            throws Exception {

        if (!FileService.ALLOW_UPLOAD) {

            throw new IOException(
                    "Uploads disabled"
            );
        }

        File directory =
                path(request);

        if (!directory.isDirectory()
                || !FileService.allowed(
                directory,
                true)) {

            throw new IOException(
                    "Upload destination is not allowed"
            );
        }

        // ✅ NEW: Get overwrite parameter from frontend
        String overwriteParam = request.getParameter("overwrite");
        boolean allowOverwrite = "true".equals(overwriteParam);

        for (Part part : request.getParts()) {

            if (!"files".equals(
                    part.getName())) {

                continue;
            }

            String submittedName =
                    part.getSubmittedFileName();

            if (submittedName == null) {
                continue;
            }

            String name =
                    Paths.get(
                            submittedName
                    )
                    .getFileName()
                    .toString();

            if (name.isEmpty()
                    || name.contains("..")) {

                continue;
            }

            File target =
                    new File(
                            directory,
                            name
                    );

            // ✅ MODIFIED: Check file exists and handle overwrite
            if (target.exists()) {
                if (!allowOverwrite) {
                    throw new IOException(
                            "File already exists: "
                                    + name
                    );
                }
                // ✅ NEW: Delete existing file before uploading new one
                if (!target.delete()) {
                    throw new IOException(
                            "Cannot delete existing file: " + name
                    );
                }
            }

            try (
                    InputStream input =
                            part.getInputStream();

                    OutputStream output =
                            new FileOutputStream(
                                    target
                            )
            ) {

                input.transferTo(output);
            }
        }

        ApiUtil.json(
                response,
                200,
                "{\"message\":\"File upload correctly finished\"}"
        );
    }

    // ============================================================
    // DOWNLOAD
    // ============================================================

    private void download(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        File file =
                FileService.resolve(
                        request.getParameter("path")
                );

        if (!file.isFile()
                || !FileService.allowed(
                file,
                false)) {

            ApiUtil.error(
                    response,
                    403,
                    "File access denied"
            );

            return;
        }

        String mimeType =
                getServletContext()
                        .getMimeType(
                                file.getName()
                        );

        if (mimeType == null) {
            mimeType =
                    "application/octet-stream";
        }

        response.setContentType(
                mimeType
        );

        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\""
                        + file.getName()
                        .replace("\"", "")
                        + "\""
        );

        response.setContentLengthLong(
                file.length()
        );

        try (
                InputStream input =
                        new BufferedInputStream(
                                new FileInputStream(file)
                        );

                OutputStream output =
                        response.getOutputStream()
        ) {

            input.transferTo(output);
        }
    }

    // ============================================================
    // READ FILE CONTENT
    // ============================================================

    private void content(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        File file =
                FileService.resolve(
                        request.getParameter("path")
                );

        if (!file.isFile()
                || !FileService.allowed(
                file,
                false)) {

            ApiUtil.error(
                    response,
                    403,
                    "File access denied"
            );

            return;
        }

        String text =
                Files.readString(
                        file.toPath(),
                        StandardCharsets.UTF_8
                );

        ApiUtil.json(
                response,
                200,
                "{\"path\":\""
                        + ApiUtil.esc(
                        file.getAbsolutePath()
                )
                        + "\",\"content\":\""
                        + ApiUtil.esc(text)
                        + "\"}"
        );
    }

    // ============================================================
    // SAVE FILE CONTENT
    // ============================================================

    private void saveContent(
            HttpServletResponse response,
            String path,
            String content,
            boolean backup)
            throws IOException {

        File file =
                FileService.resolve(path);

        if (!file.isFile()
                || !FileService.allowed(
                file,
                true)) {

            throw new IOException(
                    "File access denied"
            );
        }

        if (backup) {

            Files.copy(
                    file.toPath(),
                    new File(
                            file.getAbsolutePath()
                                    + ".bak"
                    ).toPath(),
                    StandardCopyOption
                            .REPLACE_EXISTING
            );
        }

        Files.writeString(
                file.toPath(),
                content == null
                        ? ""
                        : content,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        ApiUtil.json(
                response,
                200,
                "{\"message\":\"File saved\"}"
        );
    }

    // ============================================================
    // ZIP
    // ============================================================

    private void makeZip(
            HttpServletRequest request,
            HttpServletResponse response,
            List<String> paths)
            throws IOException {

        if (paths.isEmpty()) {

            ApiUtil.error(
                    response,
                    400,
                    "No files selected"
            );

            return;
        }

        List<File> files =
                paths.stream()
                        .map(FileService::resolve)
                        .collect(Collectors.toList());

        for (File file : files) {

            if (!FileService.allowed(
                    file,
                    false)) {

                throw new IOException(
                        "Access denied: " + file
                );
            }
        }

        response.setContentType(
                "application/zip"
        );

        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"FileManager-selection.zip\""
        );

        FileService.zip(
                files,
                response.getOutputStream()
        );
    }

    // ============================================================
    // UNPACK ZIP
    // ============================================================

    private void unpack(
            HttpServletResponse response,
            String path)
            throws Exception {

        File zipFile =
                FileService.resolve(path);

        if (!zipFile.isFile()
                || !FileService.allowed(
                zipFile,
                true)) {

            throw new IOException(
                    "Access denied"
            );
        }

        File root =
                zipFile.getParentFile();

        try (ZipFile zip =
                     new ZipFile(zipFile)) {

            Enumeration<? extends ZipEntry> entries =
                    zip.entries();

            while (entries.hasMoreElements()) {

                ZipEntry entry =
                        entries.nextElement();

                File output =
                        new File(
                                root,
                                entry.getName()
                        )
                        .getCanonicalFile();

                /*
                 * Prevent ZIP path traversal.
                 */
                if (!output.getPath()
                        .startsWith(
                                root.getCanonicalPath()
                                        + File.separator
                        )) {

                    throw new IOException(
                            "Unsafe archive entry"
                    );
                }

                if (entry.isDirectory()) {

                    output.mkdirs();

                } else {

                    if (output.exists()) {

                        throw new IOException(
                                "File already exists: "
                                        + output
                        );
                    }

                    File parent =
                            output.getParentFile();

                    if (parent != null) {
                        parent.mkdirs();
                    }

                    try (
                            InputStream input =
                                    zip.getInputStream(entry);

                            OutputStream outputStream =
                                    new FileOutputStream(
                                            output
                                    )
                    ) {

                        input.transferTo(
                                outputStream
                        );
                    }
                }
            }
        }

        ApiUtil.json(
                response,
                200,
                "{\"message\":\"Archive unpacked\"}"
        );
    }
}