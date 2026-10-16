package xyz.martelle.clapet.unlock

import kotlin.system.exitProcess

/**
 * Shizuku user service: Shizuku runs this class in its own process with the shell uid,
 * which may grant development permissions. It does one fixed thing, with no argument,
 * so it cannot be turned into a general shell.
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun grantDumpToClapet(): Int =
        ProcessBuilder("pm", "grant", CLAPET_PACKAGE, "android.permission.DUMP")
            .redirectErrorStream(true)
            .start()
            .waitFor()

    private companion object {
        const val CLAPET_PACKAGE = "xyz.martelle.clapet"
    }
}
