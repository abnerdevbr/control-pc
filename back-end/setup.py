# pip install pyinstaller
# Esse comando deve ser executado no terminal para instalar o PyInstaller, uma ferramenta usada para transformar um script Python em um executável (.exe).

import sys  # Importa o módulo 'sys' que fornece acesso a algumas variáveis usadas ou mantidas pelo interpretador Python
import os  # Importa o módulo 'os' que oferece funções para interagir com o sistema operacional, como manipulação de arquivos e diretórios
import shutil  # Importa o módulo 'shutil', que fornece funções para manipulação de arquivos e pastas
from PyInstaller.__main__ import run  # Importa a função 'run' do módulo PyInstaller, que é a responsável por executar a criação do executável
import time 

# Registra o tempo de início
inicio = time.time()
inicio_formatado = time.strftime('%H:%M:%S', time.localtime(inicio))
print(f'Tempo Início: {inicio_formatado}')


def recurso_caminho(rel_path):
    """Caminho de recurso compatível com PyInstaller (em tempo de execução)."""
    # Essa função retorna o caminho correto para arquivos quando o programa está sendo executado com PyInstaller.
    if hasattr(sys, '_MEIPASS'):
        # Quando o código está sendo executado como um .exe gerado pelo PyInstaller, _MEIPASS será definido.
        return os.path.join(sys._MEIPASS, rel_path)
    # Caso contrário, o caminho será relativo ao diretório atual.
    return os.path.join(os.path.abspath("."), rel_path)

def copiar_para_dist(arquivos_ou_pastas):
    """Copia arquivos/pastas extras para o diretório onde está o .exe."""
    # Essa função copia arquivos ou pastas adicionais para o diretório 'dist', onde o executável será gerado.
    dist_dir = os.path.join(os.getcwd(), 'dist')  # Obtém o diretório 'dist' onde o PyInstaller coloca o executável
    for item in arquivos_ou_pastas:
        origem = os.path.join(os.getcwd(), item)  # Caminho completo do item a ser copiado
        destino = os.path.join(dist_dir, item)  # Caminho completo de destino no diretório 'dist'
        if os.path.isdir(origem):
            # Se o item for um diretório, copia a pasta inteira
            shutil.copytree(origem, destino, dirs_exist_ok=True)
        elif os.path.isfile(origem):
            # Se o item for um arquivo, copia o arquivo
            shutil.copy2(origem, destino)

# Caminho para o ícone
current_dir = os.path.dirname(os.path.abspath(__file__))  # Obtém o diretório onde o script está localizado
icon_path = os.path.join('mousekeyboard.ico')  # Caminho do ícone do aplicativo

# Caminho do script principal
main_script = 'Control PC Server.py'  # Nome do script Python principal que será convertido em executável

# Verifica se o arquivo principal existe
if not os.path.isfile(main_script):
    print(f"Erro: O arquivo '{main_script}' não foi encontrado.")
    sys.exit(1)  # Se o arquivo principal não for encontrado, exibe um erro e encerra a execução

# Cria um arquivo temporário com os metadados
version_info_content = f"""# UTF-8
#
# For more details about fixed file info 'ffi' see:
# http://msdn.microsoft.com/en-us/library/ms646997.aspx
VSVersionInfo(
  ffi=FixedFileInfo(
    filevers=(1, 0, 0, 0), # Versão do arquivo
    prodvers=(1, 0, 0, 0), # Versão do produto
    mask=0x3f,
    flags=0x0,
    OS=0x40004,
    fileType=0x1,
    subtype=0x0,
    date=(0, 0)
  ),
  kids=[
    StringFileInfo(
      [
      StringTable(
        '040904B0',
        [
          StringStruct('CompanyName', 'Abner Luis'),
          StringStruct('FileDescription', 'Control PC Server'),
          StringStruct('FileVersion', '1.0.0.0'),
          StringStruct('InternalName', 'Control PC Server'),
          StringStruct('LegalCopyright', 'Copyright © 2025 Abner Luis'),
          StringStruct('OriginalFilename', 'Control PC Server.exe'),
          StringStruct('ProductName', 'Control PC Server'),
          StringStruct('ProductVersion', '1.0.0.0')
        ])
      ]),
    VarFileInfo([VarStruct('Translation', [1033, 1200])])
  ]
)
"""

# Cria um arquivo temporário com os metadados
version_info_path = os.path.join(current_dir, 'version_info.txt')
with open(version_info_path, 'w', encoding='utf-8') as f:
    f.write(version_info_content)

# Opções do PyInstaller
pyinstaller_options = [
    '--onefile',  # Gera um único arquivo .exe (em vez de vários arquivos)
    '--icon', icon_path,  # Define o ícone do aplicativo
    '--name', 'Control PC Server',  # Define o nome do aplicativo gerado
    '--exclude-module', 'tkinter',  # Exclui a biblioteca tkinter (caso não seja usada)
    '--version-file', version_info_path,  # Arquivo com metadados
    '--hidden-import', 'os',
    '--hidden-import', 'pyautogui',
    '--hidden-import', 'socket',
    '--hidden-import', 'threading',
    '--hidden-import', 'json',
    '--hidden-import', 'ctypes',
    '--hidden-import', 'ctypes.wintypes',
]

try:
    # Adiciona o script principal à lista de opções
    pyinstaller_options.append(main_script)

    # Executa o PyInstaller com as opções definidas
    run(pyinstaller_options)

    # # Copia a pasta 'Data' para o diretório 'dist', caso o PyInstaller tenha gerado múltiplos arquivos (não usei '--onefile')
    # copiar_para_dist(['Data'])

    # Calcula o tempo decorrido
    fim = time.time()
    duracao = fim - inicio
    
    # Converte para minutos e segundos
    minutos, segundos = divmod(duracao, 60)

    # Compilação concluida.
    print("\n" + "="*50)
    print(f"Compilação concluída com sucesso!")
    print(f"Tempo total de execução: {int(minutos)} minutos e {int(segundos)} segundos")
    print(f'Começado em: {inicio_formatado}')
    print(f"Finalizado em: {time.strftime('%H:%M:%S')}")
    print("="*50)

    
finally:
    # Remove o arquivo temporário de metadados
    if os.path.exists(version_info_path):
        os.remove(version_info_path)

# Depois de rodar este script, o executável será gerado na pasta 'dist' dentro do diretório onde o script está localizado.

# Rodar o progroma setup coloca no terminar "python setup.py build"