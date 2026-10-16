package xyz.martelle.clapet.unlock;

// Runs in a Shizuku user service, with the shell uid.
interface IShellService {
    // Reserved by Shizuku to stop the user service.
    void destroy() = 16777114;

    // Grants android.permission.DUMP to Clapet; returns the exit code of `pm grant`.
    int grantDumpToClapet() = 1;
}
