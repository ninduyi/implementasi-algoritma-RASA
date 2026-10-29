<#
.SYNOPSIS
    Preflight verification script untuk SOKA RASA Real-World AWS
#>
param (
    [string]$KeyPath = "$PSScriptRoot\..\..\rasa-host-key.pem",
    [string]$Host1Ip = "32.236.84.34",
    [string]$Host2Ip = "3.106.226.40"
)

& "$PSScriptRoot\demo.ps1" -PreflightOnly -KeyPath $KeyPath -Host1Ip $Host1Ip -Host2Ip $Host2Ip

