"""
Servidor de controle remoto.
Roda no PC Windows e escuta comandos vindos do app Android pela rede local.

Instalacao:
    pip install pyautogui

Uso:
    python server.py

Depois descubra o IP local do PC (ipconfig no cmd, procure "Endereco IPv4")
e coloque esse IP no app Android.
"""

import os
import socket
import threading
import json
import ctypes
import ctypes.wintypes
import pyautogui

# elimina o atraso padrao de 0.1s que o pyautogui aplica depois de CADA comando
# (isso e a principal causa de lentidao, so afeta text/key aqui, ja que
# movimento e clique agora usam a API do Windows direto, sem passar pelo pyautogui)
pyautogui.PAUSE = 0
pyautogui.FAILSAFE = False

HOST = "0.0.0.0"
PORT = 5555

user32 = ctypes.windll.user32

MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_WHEEL = 0x0800
MOUSEEVENTF_HWHEEL = 0x1000  # scroll horizontal


def get_cursor_pos():
    pt = ctypes.wintypes.POINT()
    user32.GetCursorPos(ctypes.byref(pt))
    return pt.x, pt.y


def move_relative(dx: float, dy: float):
    x, y = get_cursor_pos()
    user32.SetCursorPos(int(x + dx), int(y + dy))


def mouse_down(button: str = "left"):
    if button == "right":
        user32.mouse_event(MOUSEEVENTF_RIGHTDOWN, 0, 0, 0, 0)
    else:
        user32.mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0)


def mouse_up(button: str = "left"):
    if button == "right":
        user32.mouse_event(MOUSEEVENTF_RIGHTUP, 0, 0, 0, 0)
    else:
        user32.mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, 0)


def click(button: str = "left"):
    # clique rapido = desce e sobe o botao na hora
    mouse_down(button)
    mouse_up(button)


def scroll(dx: float, dy: float):
    # cada "clique" de roda equivale a 120 no Windows, o *40 so ajusta a sensibilidade
    if dy:
        user32.mouse_event(MOUSEEVENTF_WHEEL, 0, 0, int(dy * 40), 0)
    if dx:
        user32.mouse_event(MOUSEEVENTF_HWHEEL, 0, 0, int(dx * 40), 0)


def shutdown_pc():
    # /s = desligar, /t 3 = espera 3s antes (da uma margem de seguranca),
    # /f = forca fechar programas que estejam travando o desligamento
    os.system("shutdown /s /t 3 /f")


def process_command(cmd: dict):
    tipo = cmd.get("type")

    if tipo == "move":
        move_relative(cmd.get("dx", 0), cmd.get("dy", 0))

    elif tipo == "click":
        click(cmd.get("button", "left"))

    elif tipo == "doubleclick":
        click("left")
        click("left")

    elif tipo == "mousedown":
        # usado pra segurar o botao (ex: comeco de uma selecao de texto)
        mouse_down(cmd.get("button", "left"))

    elif tipo == "mouseup":
        # solta o botao que estava segurado
        mouse_up(cmd.get("button", "left"))

    elif tipo == "scroll":
        # dy = scroll vertical, dx = scroll horizontal, os dois podem vir juntos
        scroll(cmd.get("dx", 0), cmd.get("dy", 0))

    elif tipo == "text":
        pyautogui.write(cmd.get("value", ""))

    elif tipo == "key":
        # teclas especiais: enter, backspace, delete, esc, space, tab, etc.
        pyautogui.press(cmd.get("value"))

    elif tipo == "hotkey":
        # combinacao de teclas, ex: ["ctrl", "c"], ["ctrl", "v"] ou ["ctrl", "x"]
        keys = cmd.get("keys", [])
        if keys:
            pyautogui.hotkey(*keys)

    elif tipo == "shutdown":
        # pedido de desligar o PC vindo do app (ja confirmado do lado do celular)
        print("[!] Pedido de desligamento recebido, desligando o PC...")
        shutdown_pc()


def handle_client(conn: socket.socket, addr):
    print(f"[+] Conectado: {addr}")
    # desliga o algoritmo de Nagle: manda cada pacote na hora,
    # sem esperar acumular dados, reduz a latencia percebida
    conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)

    buffer = ""
    with conn:
        while True:
            data = conn.recv(4096)
            if not data:
                break
            buffer += data.decode("utf-8", errors="ignore")
            while "\n" in buffer:
                line, buffer = buffer.split("\n", 1)
                line = line.strip()
                if not line:
                    continue
                try:
                    cmd = json.loads(line)
                    process_command(cmd)
                except json.JSONDecodeError:
                    print(f"[!] JSON invalido recebido: {line}")
    print(f"[-] Desconectado: {addr}")


def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen(5)
    print(f"Servidor escutando em {HOST}:{PORT}")
    print("Deixe essa janela aberta enquanto usa o app.")

    while True:
        conn, addr = server.accept()
        thread = threading.Thread(target=handle_client, args=(conn, addr), daemon=True)
        thread.start()


if __name__ == "__main__":
    main()