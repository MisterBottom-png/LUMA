param(
    [int]$Port = 8765,
    [string]$ApkPath = "orbit-debug.apk"
)

$resolvedApk = Resolve-Path -LiteralPath $ApkPath
$apkBytes = [System.IO.File]::ReadAllBytes($resolvedApk)
$logPath = Join-Path (Split-Path -Parent $resolvedApk) "apk-server.log"
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $Port)
$listener.Start()
Add-Content -LiteralPath $logPath -Value "$(Get-Date -Format o) listening on $Port, serving $resolvedApk"
Write-Output "Serving $resolvedApk"
Write-Output "Local debug build download: http://localhost:$Port/orbit-debug.apk"

try {
    while ($true) {
        $client = $listener.AcceptTcpClient()
        try {
            $stream = $client.GetStream()
            $buffer = [byte[]]::new(8192)
            while ($stream.DataAvailable) {
                [void]$stream.Read($buffer, 0, $buffer.Length)
                Start-Sleep -Milliseconds 10
            }
            $headers = @(
                "HTTP/1.1 200 OK",
                "Content-Type: application/vnd.android.package-archive",
                "Content-Disposition: attachment; filename=`"orbit-debug.apk`"",
                "Content-Length: $($apkBytes.Length)",
                "Connection: close",
                "",
                ""
            ) -join "`r`n"
            $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($headers)
            $stream.Write($headerBytes, 0, $headerBytes.Length)
            $stream.Write($apkBytes, 0, $apkBytes.Length)
            $stream.Flush()
            Add-Content -LiteralPath $logPath -Value "$(Get-Date -Format o) served $($apkBytes.Length) bytes"
        } catch {
            Add-Content -LiteralPath $logPath -Value "$(Get-Date -Format o) client error: $($_.Exception.ToString())"
        } finally {
            $client.Close()
        }
    }
} finally {
    $listener.Stop()
}
