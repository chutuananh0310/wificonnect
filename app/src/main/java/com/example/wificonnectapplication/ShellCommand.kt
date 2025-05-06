import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object ShellCommand {
    private const val TAG = "ShellCommand"

    @RequiresApi(Build.VERSION_CODES.O)
    fun execute(command: String?): CommandResult {
        val output = StringBuilder()
        val error = StringBuilder()
        var process: Process? = null
        var stdoutReader: BufferedReader? = null
        var stderrReader: BufferedReader? = null
        var exitCode = -1

        try {
            process = Runtime.getRuntime().exec(command)

            // Đọc output stream
            val stdoutStream = process.getInputStream()
            stdoutReader = BufferedReader(InputStreamReader(stdoutStream))
            var line: String?
            while ((stdoutReader.readLine().also { line = it }) != null) {
                output.append(line).append("\n")
            }

            // Đọc error stream
            val stderrStream = process.getErrorStream()
            stderrReader = BufferedReader(InputStreamReader(stderrStream))
            while ((stderrReader.readLine().also { line = it }) != null) {
                error.append(line).append("\n")
            }

            if (process.waitFor(3, TimeUnit.SECONDS)) {
                exitCode = process.exitValue()
            } else {
                Log.w(TAG, "Lệnh timeout: " + command)
                process.destroy()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Lỗi thực thi lệnh: " + command, e)
            error.append("IOException: ").append(e.message)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Lệnh bị gián đoạn: " + command, e)
            error.append("InterruptedException: ").append(e.message)
        } finally {
            // Đóng resources
            if (stdoutReader != null) {
                try {
                    stdoutReader.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Lỗi đóng stdout reader", e)
                }
            }
            if (stderrReader != null) {
                try {
                    stderrReader.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Lỗi đóng stderr reader", e)
                }
            }
            if (process != null) {
                process.destroy()
            }
        }

        return CommandResult(
            exitCode,
            output.toString().trim { it <= ' ' },
            error.toString().trim { it <= ' ' })
    }

    class CommandResult(val exitCode: Int, val output: String?, val error: String?) {
        override fun toString(): String {
            return "Exit Code: " + exitCode + "\nOutput:\n" + output + "\nError:\n" + error
        }
    }
}