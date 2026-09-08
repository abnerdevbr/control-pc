# Control PC

Controle remoto do PC por Android usando a rede local.

## Instalação rápida

### 1) Instalar no Windows

Já existem os aplicativos prontos gerados na pasta:

- `aplicativo/pc-desktop-windows/Control PC Server.exe`
- `aplicativo/mobile-android/app-control-pc.apk`

#### Windows

1. Vá até a pasta `aplicativo/pc-desktop-windows`.
2. Execute o arquivo `Control PC Server.exe`.
3. Deixe a janela do servidor aberta.
4. Descubra o IP do computador:

```cmd
ipconfig
```

Procure por `Endereço IPv4`.

#### Android

1. Copie o APK `app-control-pc.apk` para o celular.
2. Instale o aplicativo.
3. Abra o app e conecte ao IP do PC.

> Se o app não encontrar automaticamente, digite o IP do computador na tela de conexão.

---

## O que é este projeto?

Este projeto transforma um celular Android em um controle remoto do mouse e teclado do computador Windows.

Ele funciona em rede local e permite:

- mover o cursor
- clicar com botão esquerdo e direito
- arrastar e selecionar texto
- rolar páginas
- digitar texto no computador
- usar atalhos do teclado
- desligar o PC com confirmação

---

## Estrutura do projeto

```text
Control PC/
├── README.md
├── aplicativo/
│   ├── mobile-android/
│   │   └── app-control-pc.apk
│   └── pc-desktop-windows/
│       └── Control PC Server.exe
├── back-end/
│   ├── Control PC Server.py
│   ├── Control PC Server.spec
│   └── setup.py
└── front-end/
    └── ControlPC Mobile/
        ├── app/
        ├── build.gradle.kts
        ├── gradle.properties
        ├── gradlew
        ├── gradlew.bat
        └── settings.gradle.kts
```

---

## Como usar

### No PC

1. Abra o executável da pasta `aplicativo/pc-desktop-windows`.
2. Verifique se apareceu a mensagem de servidor escutando.
3. Mantenha a janela aberta enquanto usa o app.
4. Descubra o IP do computador com `ipconfig`.

### No celular

1. Instale o APK `app-control-pc.apk`.
2. Abra o app.
3. Ele tenta localizar o PC automaticamente na rede local.
4. Se não encontrar, digite o IP manualmente.
5. Toque em conectar.

---

## Controles do app

### Touchpad

- 1 dedo: mover o cursor
- 2 dedos: scroll
- toque simples: clique esquerdo
- toque com 3 dedos: clique direito
- toque duplo ou longo: arrastar/selecionar

### Teclado

- digitação de texto
- Enter
- Backspace
- Delete
- atalhos como Ctrl + C / V / X
- setas, Page Up/Down, Home, Insert, F1-F12

### Desligar PC

- clique no botão de desligar
- confirme a ação no alerta
- o servidor envia o comando de shutdown para o Windows

---

## Requisitos

### Windows

- Windows 10 ou superior
- acesso à mesma rede Wi-Fi ou rede local
- permissão para controlar mouse e teclado

### Android

- Android 7+ (recomendado)
- mesma rede Wi-Fi da máquina

---

## Rodar a partir do código fonte

Se quiser compilar ou alterar o projeto manualmente:

### Backend (Python)

```bash
cd back-end
pip install pyautogui
python "Control PC Server.py"
```

### Android (Kotlin)

```bash
cd "front-end/ControlPC Mobile"
./gradlew assembleDebug
```

ou abra o projeto no Android Studio e execute no emulador ou no celular.

---

## Portas utilizadas

- TCP: `5555` — conexão do app com o servidor
- UDP: `5557` — descoberta automática na rede local

---

## Observações importantes

- O PC e o celular precisam estar na mesma rede local.
- O executável do Windows precisa ficar aberto para receber os comandos.
- Use em redes confiáveis, porque a comunicação local não inclui autenticação avançada.
- O projeto foi pensado para uso doméstico e pessoal.

---

## Solução de problemas

### App não conecta

- confirme que o executável do PC está aberto
- confirme o IP digitado
- verifique se o celular e o PC estão na mesma rede
- verifique se o firewall não está bloqueando a porta 5555

### O servidor fecha sozinho

- verifique se o Python e a dependência `pyautogui` estão instalados corretamente
- se estiver usando o executável pronto, confirme que o `.exe` foi baixado corretamente

### Cursor não responde direito

- confira se o IP está correto
- confirme que o computador certo está sendo controlado
- ajuste a sensibilidade do toques no código, se necessário

---

## Resumo rápido

- Windows: use `aplicativo/pc-desktop-windows/Control PC Server.exe`
- Android: instale `aplicativo/mobile-android/app-control-pc.apk`
- Descubra o IP com `ipconfig`
- Conecte o app ao IP do PC e controle o computador

Se quiser, também posso criar uma versão do README com aparência mais profissional para GitHub, incluindo badges, screenshots e instruções em inglês.
