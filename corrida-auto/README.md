# Corrida Auto 🚗

Gerenciador de aceite automático de corridas para motoristas de **Uber, 99 e inDrive** (no estilo do Gigu).
O app lê a oferta que aparece na tela e **toca em "Aceitar" sozinho** quando a corrida passa nos seus filtros.

## O que ele faz

| Regra | Padrão |
|---|---|
| Mínimo de **R$ por km** | R$ 1,50/km |
| Contar o km até o passageiro no R$/km | ligado |
| Valor mínimo, máx. km de busca, máx. km total | desligados |
| **Bairros de destino aceitos** (vazio = todos) | — |
| Bairros bloqueados (origem ou destino) | — |
| **Recusar automaticamente abaixo do R$/km mínimo** | ligado |
| Recusar também por bairro, distância ou valor mínimo | desligado |
| **Ignorar ofertas quando já estou em corrida** | ligado |
| **Exceto se faltar ≤ X km para terminar a corrida atual** | ligado, 1 km |

### Recusa automática

Ofertas abaixo do R$/km mínimo são recusadas sozinhas: o app toca em "Recusar" (ou "Rejeitar",
"Ignorar"… a lista fica em `rotulosRecusar` no `padroes-remotos.json`). Por segurança ele **não** recusa:

- com o aceite automático desligado (bolinha em OFF);
- durante uma corrida (aí só ignora), exceto no trecho final em que ele aceitaria;
- quando não conseguiu ler o km da oferta (para não recusar uma corrida boa por erro de leitura).

Como acha o botão de recusar: texto ("Recusar"…) → botão "X" pela descrição/id ("Fechar", `…close…`)
→ botão pequeno sem texto no canto superior direito da oferta, na altura do valor (o "X" da 99/Uber).
Se não achar, tenta de novo 3 vezes a cada 0,4 s.

No histórico: ✅ aceita, 🚫 recusada automaticamente, ❌ só ignorada.

### Diagnóstico

O botão **Copiar diagnóstico** copia tudo o que o app leu da última oferta e da última tela de corrida
(textos, descrições, ids, botões clicáveis `[C]` e posições). Cole na conversa para ajustar a leitura.

### Bolinha flutuante

Com o serviço de acessibilidade ativo, aparece uma bolinha por cima dos apps (inclusive do Uber/99):

- **Toque**: liga/desliga o aceite automático. Verde "ON" = ligado, cinza "OFF" = desligado.
- **Arrastar**: move a bolinha; ela gruda na borda mais próxima e a posição fica salva.
- **Segurar**: abre o Corrida Auto.
- **Anel azul**: você está em corrida.

Ela pode ser escondida na tela do app ("Mostrar bolinha flutuante"). Não precisa da permissão
"sobrepor a outros apps": é desenhada pelo próprio serviço de acessibilidade.

### Corrida em andamento

O app acompanha a tela do app do motorista e sabe em que fase você está:

- **Livre** ("Procurando viagens"…) → avalia normalmente.
- **Indo buscar passageiro** ("Iniciar viagem", "Cheguei"…) → **ignora** qualquer oferta.
- **Em viagem** ("Finalizar viagem"…) → lê quanto falta para o destino:
  - faltando **mais** que o limite (1 km) → **ignora**;
  - faltando **até** o limite → avalia a oferta com os filtros e aceita se passar.

Quando aceita no final de uma corrida, o **R$/km é calculado a partir do fim da corrida atual**:

```
km considerados = (distância do FIM da corrida atual até o novo passageiro) + km da nova viagem
R$/km           = valor da oferta / km considerados
```

- Se o app já mostra a busca contada do desembarque (oferta "em sequência", ex.: "após o desembarque"),
  usa esse número direto.
- Se a tela mostra a distância a partir de onde você está agora, o app usa
  `busca mostrada + km restante da corrida atual`. É o pior caso possível (pela desigualdade triangular),
  então o R$/km nunca fica inflado. No máximo fica um pouco conservador (no máximo 1 km a mais).
- Os km que ainda faltam da corrida atual **não** entram na conta: eles já são pagos pela corrida atual.

Exemplo: faltam 0,8 km, a oferta é R$ 20,00 com busca de 2 km e viagem de 10 km →
(2 + 0,8) + 10 = 12,8 km → R$ 1,56/km → **aceita** (mínimo R$ 1,50).

## Estrutura

```
core/   Kotlin puro: leitura da tela (LeitorDeOferta, LeitorDeEstado) e regras (MotorDeRegras) + testes
app/    App Android: serviço de acessibilidade, tela de configuração e histórico de decisões
```

## Instalar o APK

**Link fixo da última versão:**
https://github.com/Chatosss/Chatosss/releases/latest/download/corrida-auto.apk

1. Abra o link no celular e baixe o APK.
2. Permita "instalar apps desconhecidos" para o navegador, quando o Android pedir.
3. Abra o Corrida Auto → **Ativar serviço de acessibilidade** → ative "Corrida Auto".
4. Configure os filtros, ligue "Aceite automático" e toque em **Salvar**.

O APK é gerado pelo GitHub Actions (`.github/workflows/corrida-auto.yml`) a cada push que muda o código:
roda os testes, compila, assina e publica a release `v<número>`.

## Atualizações depois de instalado

| O que mudou | Como chega no celular | Precisa reinstalar? |
|---|---|---|
| Textos que o app procura na tela (`padroes-remotos.json`) | O app baixa sozinho a cada 5 min enquanto o serviço está rodando, ou ao tocar em **Buscar atualizações** | Não |
| Código do app | Novo APK publicado; ao abrir o app aparece **"Nova versão disponível"** → Atualizar | Só um toque para confirmar (exigência do Android) |

Na primeira atualização o Android pede para permitir que o Corrida Auto instale apps; é só permitir uma vez.
Configurações e filtros são mantidos.

### Ajustando os padrões de leitura

Se uma tela do Uber/99 não for reconhecida, adicione o texto dela em `padroes-remotos.json`
(ex.: um novo nome do botão em `rotulosAceitar`), aumente o campo `versao` e faça push.
Mudanças só nesse arquivo não geram APK novo. A tela inicial do app mostra a versão dos padrões em uso.

### Assinatura (necessária para as atualizações)

O Android só instala uma atualização por cima se ela for assinada pela mesma chave. A chave **não**
fica no repositório (que é público). Ela fica nos *secrets* do GitHub:
*Settings → Secrets and variables → Actions → New repository secret*:

| Secret | Valor |
|---|---|
| `ASSINATURA_JKS_BASE64` | o arquivo .jks em base64 |
| `ASSINATURA_SENHA` | a senha da chave |
| `ASSINATURA_ALIAS` | o alias da chave |

Para gerar uma chave: `keytool -genkeypair -keystore corrida-auto.jks -alias corrida-auto -keyalg RSA -keysize 2048 -validity 10000`
e depois `base64 -w0 corrida-auto.jks`.

Sem os secrets, o APK sai com uma chave de teste temporária. Ele funciona, mas a próxima atualização
não instala por cima: é preciso desinstalar e instalar de novo.

## Compilar localmente

Abra `corrida-auto/` no Android Studio e rode (`Run ▶`). Testes da lógica, sem Android SDK: `gradle :core:test`.

## Limitações e avisos

- **Termos de uso**: Uber, 99 e inDrive podem proibir ferramentas de automação. O uso pode levar a
  bloqueio da conta. Use por sua conta e risco.
- A leitura depende do texto que os apps mostram na tela. Quando eles mudarem o layout, ajuste os
  padrões em `LeitorDeOferta.kt` / `LeitorDeEstado.kt` e use o histórico de decisões do app para conferir.
- Se o app não conseguir ler quanto falta para terminar a corrida, ele **ignora** a oferta (por segurança).
- Comece com um atraso de aceite (ex.: 600 ms) para dar tempo de ver a decisão na tela.
