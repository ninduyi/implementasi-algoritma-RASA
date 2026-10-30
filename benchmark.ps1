<#
.SYNOPSIS
    Root launcher untuk Multi-Scale Benchmark (100 s.d. 500 Task)
#>
[CmdletBinding()]
param (
    [int[]]$Scales = @(100, 200, 300, 400, 500),
    [string]$KeyPath = "",
    [string]$Host1Ip = "32.236.84.34",
    [string]$Host2Ip = "3.106.226.40",
    [switch]$NoBrowser
)

$script = Join-Path $PSScriptRoot "scripts\benchmark.ps1"
& $script @PSBoundParameters

