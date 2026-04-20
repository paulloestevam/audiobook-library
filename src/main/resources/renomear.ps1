# --- CONFIGURAÇÃO ---
# $PSScriptRoot é a pasta onde este arquivo .ps1 está salvo
$PastaRaiz = $PSScriptRoot
$FFmpegCmd = "ffmpeg"

# Configura encoding do console para evitar erros de acentuação no log
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "--- INICIANDO RENOMEAÇÃO NA PASTA: $PastaRaiz ---" -ForegroundColor Cyan

# Pega todas as pastas dentro da Raiz
$Pastas = Get-ChildItem -Path $PastaRaiz -Directory

ForEach ($Pasta in $Pastas) {
    $NomeAtual = $Pasta.Name
    
    # 1. Verifica se o nome tem o padrão "Texto [ID]"
    # Ex: "1808 [B0CJCQSLF9]"
    if ($NomeAtual -match "^(.+?)\s\[[a-zA-Z0-9]+\]$") {
        
        $TituloLimpo = $matches[1].Trim() # A parte do nome sem o ID
        
        # 2. Procura o primeiro arquivo MP3 para ler o Artista
        $ArquivoMp3 = Get-ChildItem -LiteralPath $Pasta.FullName -Filter *.mp3 | Select-Object -First 1
        
        if ($ArquivoMp3) {
            $Artist = $null
            
            # --- Extração via FFmpeg (Mesma lógica do HTML) ---
            $RawMeta = $null
            # Redireciona erro para output para capturar
            $RawMeta = & $FFmpegCmd -hide_banner -v quiet -i $ArquivoMp3.FullName -f ffmetadata - 2>&1 
            
            if ($RawMeta) {
                foreach ($Line in $RawMeta) {
                    # Converte para string e limpa
                    $LineStr = "$Line".Trim()
                    
                    if ($LineStr -match "^artist=(.+)$") { 
                        $Artist = $matches[1].Trim()
                        break # Paramos assim que achar o artista
                    }
                }
            }

            # 3. Se encontrou o artista, renomeia
            if ($Artist) {
                # Remove caracteres inválidos para nomes de pasta (\ / : * ? " < > |)
                $ArtistaLimpo = $Artist -replace '[\\/:*?"<>|]', ''
                
                # Monta o novo nome: "Titulo - Artista"
                $NovoNome = "$TituloLimpo - $ArtistaLimpo"
                
                Write-Host "Renomeando: '$NomeAtual'" -ForegroundColor Yellow
                Write-Host "      Para: '$NovoNome'" -ForegroundColor Green
                
                try {
                    Rename-Item -LiteralPath $Pasta.FullName -NewName $NovoNome -ErrorAction Stop
                }
                catch {
                    Write-Host "   ERRO: Não foi possível renomear (Arquivo em uso ou nome já existe)." -ForegroundColor Red
                }
            } else {
                Write-Host "Pulei '$NomeAtual': Não encontrei tag 'artist' no MP3." -ForegroundColor Gray
            }
        } else {
            Write-Host "Pulei '$NomeAtual': Pasta vazia ou sem MP3." -ForegroundColor Gray
        }
    }
}

Write-Host "------------------------------------------------"
Write-Host "Concluído!" -ForegroundColor Cyan