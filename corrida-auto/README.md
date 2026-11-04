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
| **Ignorar ofertas quando já estou em corrida** | ligado |
| **Exceto se faltar ≤ X km para terminar a corrida atual** | ligado, 1 km |

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

## Como compilar e instalar

1. Instale o Android Studio e abra a pasta `corrida-auto/`.
2. Rode em um celular Android 8+ (`Run ▶`) ou gere o APK em *Build → Build APK*.
3. No app: **Ativar serviço de acessibilidade** → ative "Corrida Auto".
4. Configure os filtros, ligue "Aceite automático" e toque em **Salvar**.

Testes da lógica (não precisa de Android SDK): `gradle :core:test`.

## Limitações e avisos

- **Termos de uso**: Uber, 99 e inDrive podem proibir ferramentas de automação. O uso pode levar a
  bloqueio da conta. Use por sua conta e risco.
- A leitura depende do texto que os apps mostram na tela. Quando eles mudarem o layout, ajuste os
  padrões em `LeitorDeOferta.kt` / `LeitorDeEstado.kt` e use o histórico de decisões do app para conferir.
- Se o app não conseguir ler quanto falta para terminar a corrida, ele **ignora** a oferta (por segurança).
- Comece com um atraso de aceite (ex.: 600 ms) para dar tempo de ver a decisão na tela.
