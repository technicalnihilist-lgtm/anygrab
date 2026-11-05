package android.content;

import java.io.File;

/** Minimal stand-in so the Android-free parts can be exercised on a desktop JVM. */
public abstract class Context {
    public abstract File getCacheDir();
}
