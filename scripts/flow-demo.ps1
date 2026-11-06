# ============================================================
#  Ejecuta el flujo completo del bot de grillas contra la app en marcha.
#
#    cd C:\Users\luism\Desktop\bot-java-reactivo\app\app
#    .\mvnw.cmd spring-boot:run            # en otra terminal
#
#    powershell -ExecutionPolicy Bypass -File .\scripts\flow-demo.ps1
#
#  Opciones:
#    -Base http://localhost:8080   URL base
#    -StartFeed                    tambien arranca el feed de precios en vivo
# ============================================================
param(
    [string]$Base = "http://localhost:8080",
    [switch]$StartFeed
)

$ErrorActionPreference = "Stop"

function Wait-Bot {
    param([string]$Base)
    for ($i = 0; $i -lt 60; $i++) {
        try { Invoke-RestMethod "$Base/api/bot/status" -TimeoutSec 3 | Out-Null; return }
        catch { Start-Sleep -Seconds 2 }
    }
    throw "La app no responde en $Base. Ejecuta '.\mvnw.cmd spring-boot:run' en otra terminal."
}

function Section([string]$Title) {
    Write-Host ""
    Write-Host "========== $Title ==========" -ForegroundColor Cyan
}

function Show-Table($Items, [string[]]$Columns) {
    # Se asigna a variable y se enumeran los elementos (Invoke-RestMethod no enumera arrays).
    $rows = @($Items)
    $rows | Select-Object $Columns | Format-Table -AutoSize | Out-String | Write-Host
}

function Show-Object($Item) {
    $Item | Format-List | Out-String | Write-Host
}

function Post-Json([string]$Url, $Body) {
    Invoke-RestMethod $Url -Method Post -ContentType "application/json" -Body ($Body | ConvertTo-Json)
}

Section "Esperando a la app en $Base"
Wait-Bot -Base $Base
Write-Host "OK" -ForegroundColor Green

Section "1) Estado del bot"
$status = Invoke-RestMethod "$Base/api/bot/status"
Show-Object $status

Section "2) Crear conjunto 78000-88000, 5 niveles, reparto 10/15/20/25/30 %"
$grid = Post-Json "$Base/api/grids" @{
    floorPrice    = 78000
    ceilPrice     = 88000
    levelCount    = 5
    btcInventory  = 0.5
    usdtReserve   = 100000
    levelPercents = @(10, 15, 20, 25, 30)
    feeRate       = 0.001
    startInBtc    = $true
}
$id = $grid.id
Write-Host "Conjunto creado con id = $id (estado $($grid.status))" -ForegroundColor Green

Section "3) Niveles (banda, % y BTC asignado)"
$levels = Invoke-RestMethod "$Base/api/grids/$id/levels"
Show-Table $levels @("levelIndex", "lowerPrice", "upperPrice", "weightPercent", "btcAllocated", "holding")

Section "4) Tick 89000 (supera el techo -> vende todo)"
Post-Json "$Base/api/bot/tick" @{ price = 89000 } | Out-Null
$reloaded = Invoke-RestMethod "$Base/api/grids/$id"
Show-Object ($reloaded | Select-Object id, status, usdtReserve, cycleSells)

Section "5) Ganancia consolidada"
$pnl = Invoke-RestMethod "$Base/api/grids/$id/pnl"
Show-Object ($pnl | Select-Object pnlUsdt, pnlBtc, btcHeld, usdtReserve, totalTrades)

Section "6) CONTINUE (mismos niveles)"
$resumed = Post-Json "$Base/api/grids/$id/resolve" @{ action = "CONTINUE" }
Show-Object ($resumed | Select-Object id, status, cycleSells)

Section "7) Tick 77000 (baja del piso -> recompran)"
Post-Json "$Base/api/bot/tick" @{ price = 77000 } | Out-Null
$afterBuy = Invoke-RestMethod "$Base/api/grids/$id/levels"
Show-Table $afterBuy @("levelIndex", "holding", "entryPrice", "tradeCount")

Section "8) Historial de operaciones"
$trades = Invoke-RestMethod "$Base/api/grids/$id/trades"
Show-Table $trades @("levelIndex", "side", "price", "quantity", "pnlUsdt", "pnlBtc")

Section "9) Tick 89000 otra vez -> se agota"
Post-Json "$Base/api/bot/tick" @{ price = 89000 } | Out-Null
Write-Host "ok"

Section "10) NEW_SET 90000-100000 heredando el efectivo"
$newSet = Post-Json "$Base/api/grids/$id/resolve" @{
    action        = "NEW_SET"
    floorPrice    = 90000
    ceilPrice     = 100000
    levelCount    = 4
    levelPercents = @(40, 30, 20, 10)
}
Show-Object ($newSet | Select-Object id, floorPrice, ceilPrice, levelCount, usdtReserve, status, originSetId)

Section "11) Todos los conjuntos"
$all = Invoke-RestMethod "$Base/api/grids"
Show-Table $all @("id", "status", "floorPrice", "ceilPrice", "usdtReserve", "originSetId")

if ($StartFeed) {
    Section "12) Encender feed de precios en vivo"
    Post-Json "$Base/api/bot/start" @{} | Out-Null
    Start-Sleep -Seconds 10
    $live = Invoke-RestMethod "$Base/api/bot/status"
    Show-Object $live
    Write-Host "El bot esta operando. Detenlo con: POST $Base/api/bot/stop" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Flujo completado." -ForegroundColor Green
