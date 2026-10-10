param([int]$TargetProcessId)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$sampleProcess = Get-Process -Id $TargetProcessId
$sampleOs = Get-CimInstance Win32_OperatingSystem -OperationTimeoutSec 5
$sampleCpu = Get-CimInstance Win32_Processor -OperationTimeoutSec 5 | Select-Object -First 1
# Aggregate only the owned PID; do not emit socket addresses or unrelated process data.
$socketLines = & "$env:SystemRoot/System32/netstat.exe" -ano -p tcp
if ($LASTEXITCODE -ne 0) { throw 'TCP instrumentation failed' }
$ownedSockets = @($socketLines | Where-Object { $_ -match ('\s' + $TargetProcessId + '\s*$') })
[ordered]@{ sampledAt=(Get-Date).ToUniversalTime().ToString('o'); cpuModel=$sampleCpu.Name; logicalCpus=$sampleCpu.NumberOfLogicalProcessors; totalMemoryKiB=$sampleOs.TotalVisibleMemorySize; freeMemoryKiB=$sampleOs.FreePhysicalMemory; freeCommitKiB=$sampleOs.FreeVirtualMemory; osVersion=$sampleOs.Version; processCpuSeconds=$sampleProcess.CPU; workingSetBytes=$sampleProcess.WorkingSet64; privateBytes=$sampleProcess.PrivateMemorySize64; handles=$sampleProcess.HandleCount; tcpConnectionsAtSample=$ownedSockets.Count; measurement='phase-boundary sample, not peak'; jvmHeap='32-512MiB' } | ConvertTo-Json -Compress
