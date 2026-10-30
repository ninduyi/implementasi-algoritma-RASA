<#
.SYNOPSIS
    Root launcher shortcut untuk SOKA Real-World RASA Demo
#>
[CmdletBinding()]
param (
    [int]$Tasks = 100,
    [string]$Algorithm = "RASA",
    [switch]$PreflightOnly,
    [double]$IterationsPerMi = 0,
    [string]$KeyPath = "",
    [string]$Host1Ip = "32.236.84.34",
    [string]$Host2Ip = "3.106.226.40",
    [switch]$NoBrowser
)

$script = Join-Path $PSScriptRoot "scripts\demo.ps1"
& $script @PSBoundParameters
