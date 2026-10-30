<#
.SYNOPSIS
    Root launcher shortcut untuk preflight health check 4 VM AWS
#>
[CmdletBinding()]
param (
    [string]$KeyPath = "",
    [string]$Host1Ip = "32.236.84.34",
    [string]$Host2Ip = "3.106.226.40"
)

$script = Join-Path $PSScriptRoot "scripts\preflight.ps1"
& $script @PSBoundParameters

