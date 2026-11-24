# Launcher Apocalipse BR

[Read in English](#english)

O launcher oficial da comunidade Apocalipse BR mantém seu cliente do **Apocalipse WoW (3.3.5a)** atualizado e abre o jogo no Windows.

## Como instalar e jogar

1. Baixe o instalador `.msi` da versão mais recente. [Versões oficiais no GitHub](https://github.com/rbm4/apocalipse-wow-launcher/releases)
2. Execute o instalador e abra o launcher pelo atalho.
3. Em **Opções**, escolha **Baixar cliente** ou indique a pasta que já contém o `Wow.exe`.
4. Clique em **Atualizar e Jogar** para verificar o patch, baixar a versão atual quando necessário e abrir o jogo.

## Como conferir o que você está instalando

O código do launcher é público para que jogadores e revisores independentes possam conferir o que ele baixa, quais arquivos do jogo altera e como abre o cliente. Na página da versão que você baixou, o link da tag leva ao código correspondente.

- **Instalador**. O VirusTotal permite procurar um relatório existente usando o SHA-256 do arquivo. [Como consultar no VirusTotal](https://docs.virustotal.com/docs/searching)
- **Código-fonte**. O CodeQL ajuda revisores a encontrar possíveis falhas de segurança no código Java. [Como funciona o CodeQL](https://docs.github.com/en/code-security/concepts/code-scanning/codeql/codeql-code-scanning)

Cada verificação cobre uma parte diferente. A leitura do código permite entender o comportamento do launcher. A consulta pelo SHA-256 identifica o arquivo exato que você baixou. [Guia de verificação e critérios de confiança](.docs/security/inspection.md)

## Documentação técnica

O código é mantido para o launcher da comunidade Apocalipse BR. Os detalhes de arquitetura, compilação e publicação ficam na [documentação do projeto](.docs/AGENTS.md).

## English

The official Apocalipse BR community launcher keeps your **Apocalipse WoW (3.3.5a)** client up to date and opens the game on Windows.

### Install and play

1. Download the latest `.msi` installer. [Official GitHub releases](https://github.com/rbm4/apocalipse-wow-launcher/releases)
2. Run the installer and open the launcher from its shortcut.
3. Under **Options**, choose **Download Client** or select the folder that already contains `Wow.exe`.
4. Click **Update & Play** to check the patch, download the current version if needed, and open the game.

### How to check what you’re installing

The launcher’s source is public so players and independent reviewers can see what it downloads, which game files it changes, and how it starts the client. The tag on the release page takes you to the source for that version.

- **Installer**. VirusTotal lets you look up an existing report using the file’s SHA-256 hash. [How to search VirusTotal](https://docs.virustotal.com/docs/searching)
- **Source code**. CodeQL helps reviewers find potential security issues in Java code. [How CodeQL works](https://docs.github.com/en/code-security/concepts/code-scanning/codeql/codeql-code-scanning)

Each check covers a different part of the process. The source shows how the launcher works. A SHA-256 lookup identifies the exact file you downloaded. [Verification and trust guide](.docs/security/inspection.md)

### Technical documentation

The source is maintained for the Apocalipse BR community launcher. Architecture, build, and release details are in the [project documentation](.docs/AGENTS.md).
