param (
    [switch]$noAmazon
)

$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$PastaRaiz = $PSScriptRoot
$ArquivoJsonSaida = Join-Path $PastaRaiz "livros_amazon.json"
$FFmpegCmd = "ffmpeg"
$UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

$Pastas = Get-ChildItem -Path $PastaRaiz -Directory
$ListaLivros = @()

foreach ($Pasta in $Pastas) {
    $AudioFiles = Get-ChildItem -LiteralPath $Pasta.FullName | Where-Object { $_.Extension -match '\.(mp3|m4a|m4b)$' }
    $FirstAudio = $AudioFiles | Select-Object -First 1
    
    $Titulo = $Pasta.Name

    if ($FirstAudio) {
        $RawMeta = & $FFmpegCmd -hide_banner -v quiet -i $FirstAudio.FullName -f ffmetadata - 2>&1 
        if ($RawMeta) {
            foreach ($RawItem in $RawMeta) {
                $Line = "$RawItem"
                if ($Line -match "^album=(.+)$") { 
                    $Titulo = $matches[1].Trim()
                    break 
                }
            }
        }
    }

    $RatingStr = ""
    $ReviewCountInt = 0
    $ProductUrl = ""

    if (-not $noAmazon) {
        $TempoEspera = Get-Random -Minimum 5 -Maximum 16
        Write-Host "Processando: $Titulo | Aguardando ${TempoEspera}s..." -ForegroundColor DarkYellow
        Start-Sleep -Seconds $TempoEspera
        
        try {
            $SearchQuery = $Titulo -replace '\(.*\)', '' -replace '\[.*\]', '' -replace '\.mp3', '' -replace '(?i)\(?Unabridged\)?', ''
            $SearchUrl = "https://www.amazon.com.br/s?k=" + [uri]::EscapeDataString($SearchQuery)
            $WebResponse = Invoke-WebRequest -Uri $SearchUrl -UserAgent $UserAgent -UseBasicParsing -ErrorAction Stop
            $HtmlContent = $WebResponse.Content
            
            if ($HtmlContent -match 'href="(/[^"]+/dp/[A-Z0-9]{10}[^"]*)"') { 
                $ProductUrl = "https://www.amazon.com.br" + $matches[1]
            }
            
            if ($HtmlContent -match '(\d+,\d) de 5 estrelas') {
                $RatingStr = $matches[1]
                
                $ReviewCountStr = "?"
                if ($HtmlContent -match 'aria-label="([\d,.]+(?:\s+mil)?)\s+(classificações|avaliações)"') { $ReviewCountStr = $matches[1] }
                elseif ($HtmlContent -match '>\(([\d,.]+(?:\s*mil)?)\)<') { $ReviewCountStr = $matches[1] }

                if ($ReviewCountStr -ne "?") {
                    $TempCount = $ReviewCountStr.ToLower().Trim()
                    if ($TempCount -match "mil") {
                        $TempCount = $TempCount -replace "mil","" -replace "\s","" -replace ",","."
                        $ReviewCountInt = [int]([double]$TempCount * 1000)
                    } else {
                        $TempCount = $TempCount -replace "\.","" -replace ",",""
                        $ReviewCountInt = [int]$TempCount
                    }
                }
                Write-Host "  -> ACHOU ($RatingStr | $ReviewCountInt reviews)" -ForegroundColor Green
            } else {
                Write-Host "  -> NAO ACHOU RATING" -ForegroundColor Red
            }
        } catch {
            Write-Host "  -> ERRO DE REDE" -ForegroundColor Red
        }
    }

    $DadosLivro = [PSCustomObject]@{
        Titulo      = $Titulo
        ReviewsQtd  = $ReviewCountInt
        RatingTexto = $RatingStr
		Url         = $ProductUrl
    }

    $ListaLivros += $DadosLivro
}

$JsonContent = $ListaLivros | ConvertTo-Json -Depth 2 -Compress
$JsonContent | Out-File -FilePath $ArquivoJsonSaida -Encoding utf8
Write-Host "JSON gerado: $ArquivoJsonSaida" -ForegroundColor Cyan