package com.filemanager.api;

import java.io.*;
import java.nio.file.*;
import java.text.DateFormat;
import java.util.*;
import java.util.zip.*;

/** Filesystem and server configuration for FileManager. */
final class FileService {
    static final boolean NATIVE_COMMANDS = Config.bool("FILEMANAGER_NATIVE_COMMANDS", false);
    static final boolean READ_ONLY = Config.bool("FILEMANAGER_READ_ONLY", false);
    static final boolean ALLOW_UPLOAD = Config.bool("FILEMANAGER_ALLOW_UPLOAD", true);
    static final boolean RESTRICT_BROWSING = Config.bool("FILEMANAGER_RESTRICT_BROWSING", true);
    static final boolean RESTRICT_WHITELIST = Config.bool("FILEMANAGER_RESTRICT_WHITELIST", true);
    static final String RESTRICT_PATH = Config.value("FILEMANAGER_ROOTS", "/data");
    static final long MAX_PROCESS_RUNNING_TIME = Config.longValue("FILEMANAGER_COMMAND_TIMEOUT_MS", 30_000L);

    private FileService() {}

    static File defaultRoot() {
        String[] roots = configuredRoots();
        return new File(roots[0]).getAbsoluteFile();
    }

    static String[] configuredRoots() {
        return Arrays.stream(RESTRICT_PATH.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    static File resolve(String path) {
        if (path == null || path.trim().isEmpty()) return defaultRoot();
        return new File(path).getAbsoluteFile();
    }

    static boolean allowed(File path, boolean write) throws IOException {
        File p = path.getCanonicalFile();
        if (!RESTRICT_BROWSING) return !(READ_ONLY && write);
        boolean in = false;
        for (String root : configuredRoots()) {
            File r = new File(root).getCanonicalFile();
            String ps = p.getPath(), rs = r.getPath();
            if (ps.equals(rs) || ps.startsWith(rs + File.separator)) {
                in = true;
                break;
            }
        }
        boolean browse = RESTRICT_WHITELIST ? in : !in;
        return browse && (!write || !READ_ONLY);
    }

    static String jsonEntry(File f) {
        String mod = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(f.lastModified()));
        return "{\"name\":\"" + ApiUtil.esc(f.getName()) + "\",\"path\":\"" + ApiUtil.esc(f.getAbsolutePath()) + "\",\"directory\":" + f.isDirectory() + ",\"size\":" + (f.isDirectory() ? 0 : f.length()) + ",\"modified\":\"" + ApiUtil.esc(mod) + "\"}";
    }

    static File[] children(File dir, String sort) {
        File[] fs = dir.listFiles();
        if (fs == null) return new File[0];
        Arrays.sort(fs, (a, b) -> {
            int d;
            if ("size".equals(sort)) d = Long.compare(a.length(), b.length());
            else if ("date".equals(sort)) d = Long.compare(a.lastModified(), b.lastModified());
            else d = a.getName().compareToIgnoreCase(b.getName());
            if (d == 0) d = a.getName().compareToIgnoreCase(b.getName());
            return d;
        });
        return fs;
    }

    static void copy(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) throw new IOException("Cannot create " + dst);
            File[] fs = src.listFiles();
            if (fs != null) for (File f : fs) copy(f, new File(dst, f.getName()));
        } else {
            if (dst.exists()) throw new IOException("File already exists: " + dst);
            try (InputStream in = new BufferedInputStream(new FileInputStream(src)); OutputStream out = new BufferedOutputStream(new FileOutputStream(dst))) {
                byte[] b = new byte[65536]; int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
            }
        }
    }

    static void delete(File f) throws IOException {
        if (f.isDirectory()) {
            File[] fs = f.listFiles();
            if (fs != null) for (File c : fs) delete(c);
        }
        if (!f.delete()) throw new IOException("Cannot delete " + f.getAbsolutePath());
    }

    static void zip(List<File> files, OutputStream out) throws IOException {
        try (ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(out))) {
            byte[] b = new byte[65536];
            for (File f : files) zipOne(f, f.getName(), z, b);
        }
    }

    static void zipOne(File f, String name, ZipOutputStream z, byte[] b) throws IOException {
        if (f.isDirectory()) {
            z.putNextEntry(new ZipEntry(name + "/")); z.closeEntry();
            File[] fs = f.listFiles();
            if (fs != null) for (File c : fs) zipOne(c, name + "/" + c.getName(), z, b);
        } else {
            z.putNextEntry(new ZipEntry(name));
            try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
                int n; while ((n = in.read(b)) != -1) z.write(b, 0, n);
            }
            z.closeEntry();
        }
    }

    static String process(String command, String dir) throws IOException {
        String[] cmd;
        if (isWindows()) cmd = new String[]{"cmd", "/C", command};
        else cmd = new String[]{"/bin/sh", "-c", command};
        Process p = new ProcessBuilder(cmd).directory(resolve(dir)).redirectErrorStream(true).start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line; while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        try { p.waitFor(MAX_PROCESS_RUNNING_TIME, java.util.concurrent.TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (p.isAlive()) { p.destroyForcibly(); sb.append("\n!!!! Process timed out and was destroyed !!!!\n"); }
        return sb.toString();
    }

    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); }
}
