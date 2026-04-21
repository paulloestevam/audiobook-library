$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$PastaRaiz = $PSScriptRoot
$PastaBiblioteca = Join-Path $PastaRaiz "00 biblioteca"
$PastaImagens = Join-Path $PastaBiblioteca "images"
$PastaDownloads = Join-Path $PastaBiblioteca "downloads"
$ArquivoSaida = Join-Path $PastaBiblioteca "index.html"
$ArquivoJson = Join-Path $PastaRaiz "livros_amazon.json"
$FFmpegCmd = "ffmpeg"

$NomeArquivoApk = "Smart AudioBook Player v10.7.0 Premium Mod Apk {CracksHash}.apk"
$LinkApk = "downloads/" + [uri]::EscapeDataString($NomeArquivoApk)

$UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

if (!(Test-Path -Path $PastaBiblioteca)) { New-Item -ItemType Directory -Path $PastaBiblioteca | Out-Null }
if (!(Test-Path -Path $PastaImagens)) { New-Item -ItemType Directory -Path $PastaImagens | Out-Null }
if (!(Test-Path -Path $PastaDownloads)) { New-Item -ItemType Directory -Path $PastaDownloads | Out-Null }

$AmazonData = @()
if (Test-Path $ArquivoJson) {
    $AmazonData = Get-Content $ArquivoJson -Raw -Encoding UTF8 | ConvertFrom-Json
}

$Pastas = Get-ChildItem -Path $PastaRaiz -Directory
$Pastas = $Pastas | Where-Object { $_.Name -ne "00 biblioteca" }
$TotalLivros = $Pastas.Count

$HtmlHeader = @"
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Biblioteca</title>
    <style>
        ...
    </style>
</head>
<body>
<div class="controls">
    <div class="header-top">
        <div class="header-title">
            <h1>&#128218; Biblioteca</h1>
            <span class="book-count">Total de livros: $TotalLivros</span>
        </div>
        <a href="$LinkApk" class="apk-link" title="Baixar APK Player">&#128242; Baixar App Player</a>
    </div>
    <div class="header-bottom">
        <div class="search-group">
            <input type="text" id="search-input" class="search-input" placeholder="Pesquisar..." onkeyup="filterBooks()">
            <select id="sort-select" class="sort-select" onchange="sortBooks()">
                <option value="title">Nome</option>
                <option value="author">Autor</option>
                <option value="duration">Dura&ccedil;&atilde;o</option>
                <option value="rating">Popularidade</option>
            </select>
        </div>
        <div class="btn-group">
            <button onclick="setMode('grid')" id="btn-grid" >Blocos</button>
            <button onclick="setMode('compact')" id="btn-compact" class="active">Compacto</button>
            <button onclick="setMode('list')" id="btn-list">Lista</button>
        </div>
    </div>
</div>
<div id="book-container" class="mode-compact">
"@

$HtmlBody = ""
$ContadorLivro = 0

Write-Host "--- INICIANDO VARREDURA ---" -ForegroundColor Cyan

ForEach ($Pasta in $Pastas) {
    $ContadorLivro++
    
    $AudioFiles = Get-ChildItem -LiteralPath $Pasta.FullName | Where-Object { $_.Extension -match '\.(mp3|m4a|m4b)$' }
    $FirstAudio = $AudioFiles | Select-Object -First 1
    $JpgFile    = Get-ChildItem -LiteralPath $Pasta.FullName -Filter *.jpg | Select-Object -First 1
    
    $NomeSeguro = $Pasta.Name -replace '[^a-zA-Z0-9\-\.]','_' 
    $NomeImagemDestino = "$NomeSeguro.jpg"

    if ($JpgFile) { 
        $CaminhoOrigem = $JpgFile.FullName
        $CaminhoDestino = Join-Path $PastaImagens $NomeImagemDestino
        Copy-Item -LiteralPath $CaminhoOrigem -Destination $CaminhoDestino -Force
        $ImgSrc = "images/$NomeImagemDestino"
    } else { 
        $ImgSrc = "https://via.placeholder.com/300x300?text=Sem+Capa" 
    }

    $Artist = "Desconhecido"
    $Album = $Pasta.Name
    $Genre = "-"
    $Desc = "Sem descrição."
    $TempoTotalSegundos = 0

    if ($FirstAudio) {
        $RawMeta = & $FFmpegCmd -hide_banner -v quiet -i $FirstAudio.FullName -f ffmetadata - 2>&1 
        if ($RawMeta) {
            $LendoDescricao = $false
            foreach ($RawItem in $RawMeta) {
                $Line = "$RawItem"
                if ($Line -match "^artist=(.+)$") { $Artist = $matches[1].Trim(); $LendoDescricao = $false }
                elseif ($Line -match "^album=(.+)$") { $Album = $matches[1].Trim(); $LendoDescricao = $false }
                elseif ($Line -match "^genre=(.+)$") { $Genre = $matches[1].Trim(); $LendoDescricao = $false }
                elseif ($Line -match "^(LONG_COMMENT|comment|description|synopsis)=(.+)$") { $Desc = $matches[2].Trim(); $LendoDescricao = $true }
                elseif ($LendoDescricao -and $Line -notmatch "^[a-zA-Z0-9_]+=") { $Desc = "$Desc " + $Line.Trim().TrimEnd('\') }
                else { if ($Line -match "^[a-zA-Z0-9_]+=") { $LendoDescricao = $false } }
            }
        }
    }

    # LIMPEZA DO NOME MOVIDA PARA ANTES DA BUSCA DO JSON
    $Album = $Album -replace '(?i)\(?Unabridged\)?', ''
    $Album = $Album.Trim()

    if ($Desc) {
        $Desc = [System.Text.RegularExpressions.Regex]::Replace($Desc, "\\u(?<Value>[a-fA-F0-9]{4})", { param($m); return [char][int]::Parse($m.Groups['Value'].Value, [System.Globalization.NumberStyles]::HexNumber) })
        $Desc = $Desc -replace '\\r', ' '
        $Desc = $Desc -replace '\\n', ' '
        $Desc = $Desc -replace '\\(.)', '$1'
        $Desc = $Desc -replace '[\r\n]+', ' ' 
        $Desc = $Desc -replace '\s+', ' '     
        $Desc = $Desc.Replace('"', "'")       
        $Desc = $Desc.Trim()
    }

    Write-Host "[$ContadorLivro/$TotalLivros] Processando: $($Pasta.Name)..." -ForegroundColor Gray
    foreach ($File in $AudioFiles) {
        $RawInfo = & $FFmpegCmd -hide_banner -i $File.FullName 2>&1
        if ($RawInfo) {
            foreach ($LineInfo in $RawInfo) {
                if ("$LineInfo" -match "Duration:\s+(\d+):(\d+):(\d+(?:[\.,]\d+)?)") {
                    $Horas = [int]$matches[1]; $Minutos = [int]$matches[2]
                    $SegundosStr = $matches[3] -replace '[^0-9\.,]', '' -replace ',', '.'
                    $SegundosDouble = 0.0
                    if ([double]::TryParse($SegundosStr, [System.Globalization.NumberStyles]::Any, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$SegundosDouble)) {
                        $TempoTotalSegundos += ($Horas * 3600) + ($Minutos * 60) + $SegundosDouble
                    }
                    break 
                }
            }
        }
    }

    $Rating = 0
    $ReviewCountInt = 0 
    $RatingHtml = '<div class="rating-none">Sem avalia&ccedil;&otilde;es</div>'
    $ProductUrl = ""

    if ($AmazonData) {
        # Busca exata (agora com o nome limpo)
        $JsonBook = $AmazonData | Where-Object { $_.Titulo -eq $Album } | Select-Object -First 1
        
        # Se falhar, tenta ver se o titulo do json está contido no nome da pasta
        if (-not $JsonBook) {
             $JsonBook = $AmazonData | Where-Object { $Pasta.Name -match [System.Text.RegularExpressions.Regex]::Escape($_.Titulo) } | Select-Object -First 1
        }

        if ($JsonBook) {
            $RatingStr = $JsonBook.RatingTexto
            $Rating = [double]::Parse($RatingStr.Replace(',', '.'))
            $ReviewCountInt = [int]$JsonBook.ReviewsQtd
            $ProductUrl = $JsonBook.Url

            if ($ProductUrl) { $RatingHtml = "<a href=""$ProductUrl"" target=""_blank"" class=""rating-container"" onclick=""event.stopPropagation()"">" } else { $RatingHtml = "<div class=""rating-container"">" }
            $RatingHtml += "<span class=""rating-stars"">&#9733; $RatingStr</span>"
            if ($ReviewCountInt -gt 0) { $RatingHtml += "<span class=""rating-count"">($ReviewCountInt)</span>" }
            if ($ProductUrl) { $RatingHtml += "</a>" } else { $RatingHtml += "</div>" }
        }
    }

    $FinalHoras = [math]::Floor($TempoTotalSegundos / 3600)
    $FinalMinutos = [math]::Floor(($TempoTotalSegundos % 3600) / 60)
    $TempoFormatado = "${FinalHoras}h${FinalMinutos}m"
    $LinkDownload = "downloads/$($Pasta.Name).zip"
    $SafeTitle = $Album.Replace('"', '&quot;')
    $SafeAuthor = $Artist.Replace('"', '&quot;')

    $HtmlBody += "<div class=""book-card"" onclick=""toggleCard(this)"" data-title=""$SafeTitle"" data-author=""$SafeAuthor"" data-duration=""$TempoTotalSegundos"" data-rating=""$Rating"" data-review-count=""$ReviewCountInt"">"
    
    $HtmlBody += '  <div class="cover-wrapper">'
    $HtmlBody += "      <img src=""$ImgSrc"" alt=""Background"" class=""cover-bg-blur"" loading=""lazy"">"
    $HtmlBody += "      <img src=""$ImgSrc"" alt=""Capa"" class=""cover-img"" loading=""lazy"">"
    
        $HtmlBody += "      <a href=""$LinkDownload"" download class=""btn-download"" title=""Baixar .zip"" onclick=""event.stopPropagation()"">"
        $HtmlBody += '          <svg viewBox="0 0 24 24"><path d="M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z"/></svg>'
        $HtmlBody += "      </a>"

    $HtmlBody += '  </div>'
    
    $HtmlBody += "  <div class=""section-title""><div class=""title"">$Album</div></div>"

    $HtmlBody += '  <div class="section-meta">'
    $HtmlBody += "      $RatingHtml"
    $HtmlBody += "      <div class=""author"">Autor: $Artist <span class=""grid-time"">($TempoFormatado)</span></div>"
    $HtmlBody += "      <div class=""list-time"">Dura&ccedil;&atilde;o: $TempoFormatado</div>"
    $HtmlBody += '  </div>'

    $HtmlBody += '  <div class="section-desc">'
    $HtmlBody += "      <div class=""meta-tag"">$Genre</div>"
    $HtmlBody += "      <div class=""description"">$Desc</div>"
    $HtmlBody += '  </div>'
    
    $HtmlBody += '</div>'
} 

$HtmlFooter = '
</div>
<script>
    function setMode(mode) {
        const c = document.getElementById("book-container");
        const bGrid = document.getElementById("btn-grid");
        const bCompact = document.getElementById("btn-compact");
        const bList = document.getElementById("btn-list");
        const cards = document.querySelectorAll(".book-card");
        cards.forEach(card => card.classList.remove("active"));
        
        bGrid.classList.remove("active");
        bCompact.classList.remove("active");
        bList.classList.remove("active");

        if (mode === "grid") { 
            c.className = "mode-grid"; 
            bGrid.classList.add("active");
        } 
        else if (mode === "compact") { 
            c.className = "mode-compact"; 
            bCompact.classList.add("active");
        }
        else { 
            c.className = "mode-list"; 
            bList.classList.add("active");
        }
    }
    function toggleCard(element) {
        const container = document.getElementById("book-container");
        if (container.classList.contains("mode-grid") || container.classList.contains("mode-compact")) {
            const isAlreadyActive = element.classList.contains("active");
            const allCards = document.querySelectorAll(".book-card");
            allCards.forEach(card => card.classList.remove("active"));
            if (!isAlreadyActive) { element.classList.add("active"); }
        }
    }
    function sortBooks() {
        const container = document.getElementById("book-container");
        const criteria = document.getElementById("sort-select").value;
        const cards = Array.from(container.getElementsByClassName("book-card"));
        cards.sort((a, b) => {
            if (criteria === "duration") { 
                let valA = parseFloat(a.getAttribute("data-duration"));
                let valB = parseFloat(b.getAttribute("data-duration"));
                return valA - valB; 
            }
            else if (criteria === "rating") { 
                let valA = parseFloat(a.getAttribute("data-review-count") || 0);
                let valB = parseFloat(b.getAttribute("data-review-count") || 0);
                return valB - valA; 
            }
            else { 
                let valA = a.getAttribute("data-" + criteria);
                let valB = b.getAttribute("data-" + criteria);
                return valA.toLowerCase().localeCompare(valB.toLowerCase()); 
            }
        });
        cards.forEach(card => container.appendChild(card));
    }
    function filterBooks() {
        const query = document.getElementById("search-input").value.toLowerCase();
        const cards = document.querySelectorAll(".book-card");
        cards.forEach(card => {
            const title = card.getAttribute("data-title").toLowerCase();
            const author = card.getAttribute("data-author").toLowerCase();
            if (title.includes(query) || author.includes(query)) {
                card.style.display = "";
            } else {
                card.style.display = "none";
            }
        });
    }
</script>
</body></html>
'
$ConteudoFinal = $HtmlHeader + $HtmlBody + $HtmlFooter
$ConteudoFinal | Out-File -FilePath $ArquivoSaida -Encoding utf8

Write-Host "--- REDIMENSIONANDO IMAGENS (FFMPEG) ---" -ForegroundColor Cyan
Set-Location -Path $PastaImagens
Get-ChildItem *.jpg | ForEach-Object {
    $tempName = "temp_$($_.Name)"
    & $FFmpegCmd -hide_banner -loglevel error -i $_.Name -vf "scale=416:-1" -q:v 5 -y $tempName
    Move-Item $tempName $_.Name -Force
}
Set-Location -Path $PastaRaiz

Write-Host "--------------------------------------------------"
Write-Host "Biblioteca gerada em: $PastaBiblioteca" -ForegroundColor Green