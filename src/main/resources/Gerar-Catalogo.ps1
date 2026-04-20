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
        :root { --bg-color: #f3f3f3; --card-bg: #ffffff; --text-main: #111; --text-sec: #555; --accent: #e09f00; --star-color: #ffa41c; }
        body { font-family: 'Segoe UI', sans-serif; background-color: var(--bg-color); margin: 0; padding: 20px; }
        
        .controls { margin-bottom: 20px; background: #fff; padding: 15px; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.05); }
        .header-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; border-bottom: 1px solid #eee; padding-bottom: 10px; }
        .header-bottom { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
        .header-title { display: flex; align-items: baseline; gap: 15px; }
        h1 { margin: 0; font-size: 1.5rem; }
        .book-count { font-size: 0.9rem; color: #777; font-weight: normal; background: #eee; padding: 2px 8px; border-radius: 10px; }
        
        .apk-link { font-size: 0.75rem; color: #999; text-decoration: none; display: flex; align-items: center; gap: 5px; transition: 0.2s; }
        .apk-link:hover { color: var(--accent); text-decoration: underline; }
        
        .search-group { display: flex; gap: 10px; align-items: center; }
        .sort-select { padding: 8px; border-radius: 4px; border: 1px solid #ddd; background: #f9f9f9; color: #333; cursor: pointer; outline: none; min-width: 120px; }
        .search-input { padding: 8px; border-radius: 4px; border: 1px solid #ddd; background: #f9f9f9; color: #333; outline: none; width: 140px; }
        .search-input:focus { border-color: var(--accent); background: #fff; }

        .btn-group button { padding: 8px 16px; border: none; cursor: pointer; background: #ddd; font-weight: bold; border-radius: 4px; margin-left: 5px; transition: 0.2s; }
        .btn-group button.active { background-color: var(--accent); color: white; }
        
        #book-container { display: grid; }
        
        .mode-grid { 
            grid-template-columns: repeat(4, 1fr); 
            gap: 50px; 
        }
        
        .mode-grid .book-card { 
            background: var(--card-bg); 
            border-radius: 8px; 
            overflow: hidden; 
            box-shadow: 0 2px 8px rgba(0,0,0,0.1); 
            display: flex; flex-direction: column; 
            position: relative; cursor: pointer; 
        }
        
        .mode-grid .cover-wrapper { order: 1; position: relative; width: 100%; aspect-ratio: 1/1; overflow: hidden; background: #eee; }
        .mode-grid .section-title { order: 2; padding: 15px 15px 5px 15px; background: #fff; } 
        .mode-grid .section-meta  { order: 3; padding: 0 15px 15px 15px; background: #fff; }  
        
        .section-desc { 
            display: block; position: absolute; top: 0; left: 0; width: 100%; height: 100%; 
            background: rgba(255, 255, 255, 0.98); color: #222; 
            padding: 20px; box-sizing: border-box; 
            opacity: 0; visibility: hidden; pointer-events: none; 
            transition: opacity 0.2s ease; overflow-y: auto; z-index: 50; 
        }
        .book-card.active .section-desc { opacity: 1; visibility: visible; pointer-events: auto; }

        .cover-bg-blur { position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: cover; filter: blur(10px) brightness(0.9); transform: scale(1.1); z-index: 1; }
        .cover-img { position: relative; width: 100%; height: 100%; object-fit: contain; z-index: 2; image-rendering: high-quality; transform: translateZ(0); box-shadow: 0 0 10px rgba(0,0,0,0.2); }
        .title { font-weight: bold; font-size: 1.1rem; height: 3.2rem; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
        .author { font-size: 0.95rem; color: var(--text-sec); margin-top: 5px; }
        .list-time { display: none; }
        .grid-time { font-weight: normal; font-size: 0.8rem; opacity: 0.8; }
        .meta-tag { display: block; font-size: 0.8rem; text-transform: uppercase; color: var(--accent); margin-bottom: 8px; font-weight: bold; }

        .mode-compact {
            grid-template-columns: repeat(8, 1fr);
            gap: 15px;
        }
        .mode-compact .book-card {
            background: var(--card-bg);
            border-radius: 6px;
            overflow: hidden;
            box-shadow: 0 2px 5px rgba(0,0,0,0.1);
            display: flex; flex-direction: column;
            position: relative;
            cursor: pointer;
        }
        
        .mode-compact .cover-wrapper { order: 1; position: relative; width: 100%; aspect-ratio: 1/1; overflow: hidden; background: #eee; }
        .mode-compact .section-title { order: 2; padding: 8px 8px 2px 8px; background: #fff; }
        .mode-compact .section-meta { order: 3; padding: 0 8px 8px 8px; background: #fff; }
        .mode-compact .title { font-size: 0.85rem; height: 2.4rem; line-height: 1.2; margin-bottom: 2px; }
        .mode-compact .author { font-size: 0.75rem; margin-top: 2px; }
        .mode-compact .grid-time { font-size: 0.7rem; }
        .mode-compact .list-time { display: none; }
        .mode-compact .rating-stars { font-size: 0.7rem; }
        .mode-compact .rating-container { font-size: 0.7rem; margin-bottom: 2px; }
        .mode-compact .meta-tag { display: block; font-size: 0.65rem; margin-bottom: 4px; }
        .mode-compact .btn-download { width: 28px; height: 28px; top: 5px; right: 5px; }

        .mode-list { 
            grid-template-columns: 1fr; 
            gap: 20px;
        } 
        
        .mode-list .book-card { 
            display: grid; 
            grid-template-columns: 180px 220px 1fr; 
            grid-template-rows: min-content 1fr;
            grid-template-areas: 
                "img meta title"
                "img meta content";
            background: var(--card-bg); 
            border-radius: 8px; 
            padding: 15px; 
            box-shadow: 0 1px 4px rgba(0,0,0,0.1); 
            column-gap: 25px; 
            row-gap: 5px;
            align-items: start;
        }

        .mode-list .cover-wrapper { grid-area: img; width: 100%; height: 180px; position: relative; border-radius: 4px; overflow: hidden; }
        .mode-list .section-meta  { grid-area: meta; display: flex; flex-direction: column; justify-content: flex-start; gap: 10px; padding-top: 5px; border-right: 1px solid #eee; padding-right: 15px;}
        
        .mode-list .section-title { grid-area: title; margin: 0; padding: 0; }
        .mode-list .title { font-size: 1.5rem; font-weight: bold; color: var(--text-main); margin-bottom: 5px; display: block; height: auto; -webkit-line-clamp: unset; }
        
        .mode-list .section-desc { 
            grid-area: content; 
            position: static; opacity: 1; visibility: visible; pointer-events: auto; 
            background: none; padding: 0; color: inherit; display: block;
        }

        .mode-list .cover-bg-blur { display: none; } 
        .mode-list .cover-img { width: 100%; height: 100%; object-fit: cover; }
        
        .mode-list .author { font-size: 1rem; font-weight: 600; color: var(--text-main); }
        .mode-list .grid-time { display: none; }
        .mode-list .list-time { display: block; font-size: 0.9rem; color: #555; font-weight: 500;}
        
        .mode-list .meta-tag { display: inline-block; background: #eee; padding: 3px 8px; font-size: 0.8rem; border-radius: 12px; margin-bottom: 10px; width: fit-content; color: #555; }
        .mode-list .description { 
            font-size: 0.85rem; 
            line-height: 1.5; color: #444; 
            border-top: none; padding-top: 0; 
        }

        .btn-download { position: absolute; top: 10px; right: 10px; width: 36px; height: 36px; background: rgba(255, 255, 255, 0.95); border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 2px 4px rgba(0,0,0,0.3); color: #333; opacity: 1; transition: opacity 0.2s; z-index: 60; }
        .mode-list .btn-download { opacity: 1; top: 5px; right: 5px; width: 28px; height: 28px; background: rgba(255,255,255,0.8); }
        .btn-download svg { width: 18px; height: 18px; fill: currentColor; }

        .rating-container { display: inline-flex; align-items: center; font-size: 0.85rem; color: #007185; margin-bottom: 5px; text-decoration: none; transition: opacity 0.2s; }
        .rating-stars { color: var(--star-color); margin-right: 5px; font-size: 0.9rem; letter-spacing: 1px; }
        .rating-count { color: #565959; font-size: 0.8rem; }
        
        @media (max-width: 1600px) { .mode-compact { grid-template-columns: repeat(6, 1fr); } }
        @media (max-width: 1400px) { .mode-grid { grid-template-columns: repeat(3, 1fr); } }
        @media (max-width: 1200px) { .mode-compact { grid-template-columns: repeat(4, 1fr); } }
        @media (max-width: 900px) { 
            .mode-grid { grid-template-columns: repeat(2, 1fr); } 
            .mode-list .book-card { grid-template-columns: 1fr; grid-template-areas: "img" "title" "meta" "content"; gap: 10px; grid-template-rows: auto; }
            .mode-list .section-meta { border-right: none; padding-right: 0; flex-direction: row; flex-wrap: wrap; align-items: center;}
        }
        @media (max-width: 500px) { 
            .mode-grid { grid-template-columns: 1fr; } 
            .mode-compact { grid-template-columns: repeat(2, 1fr); }
        }
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