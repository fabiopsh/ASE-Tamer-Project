# Archivio d’ambra

Il gioco assume l’aspetto di un archivio da campo: strumenti in smalto petrolio,
carta avorio, incisioni in rame e sigilli pixel. La direzione nasce dai suoi
elementi astratti — memoria, tempo, silenzio, eco — e usa un linguaggio da
catalogazione di fenomeni, senza riferimenti a console o franchise esistenti.

## Sistema visivo

`src/styles/tokens.css` contiene le sole 16 tinte della UI, le cornici, gli
spessori, le ombre nette, i cursori e i tempi. Le immagini delle creature
esistenti conservano la propria tavolozza. I componenti mantengono i token
semantici `--el` e `--rarity`.

| Ruolo | Inchiostri |
| --- | --- |
| Sfondo e fondo degli strumenti | `ink`, `deep` |
| Superfici e hover | `panel`, `raised` |
| Incisioni e separatori | `edge` |
| Testo primario, secondario, attenuato | `paper`, `secondary`, `muted` |
| Accento principale e rilievo dei pulsanti | `copper`, `umber` |
| Accento secondario, informazioni, eco | `jade` |
| Successo | `moss` |
| Pericolo, caos | `coral` |
| Avviso, tempo, rarità leggendaria | `amber` |
| Sogno, gravità | `rose`, `slate` |

Le cornici sono incise all’interno dei box. Due graffe opposte in rame
identificano pannelli e schede. Nessun nuovo bordo aumenta gli ingombri.
I glifi di navigazione, il sigillo e i cursori sono SVG originali del reskin;
gli SVG usano gli stessi inchiostri della UI.

Amber UI e Amber Display sono derivazioni pixel, rinominate e distribuite con
licenza OFL, dei font già presenti nel progetto. Conservano avanzamenti,
kerning, HVAR, MVAR, metriche verticali e accenti italiani. Sono inclusi i pesi
450, 550, 700, 800 e 900; l’ultimo serve anche ai `strong` del browser.
La faccia della navigazione viene caricata prima del mount per preservare la
misura usata dallo scroll esistente. Il fallimento del caricamento mantiene il fallback.

Gli asset WOFF2 sono versionati; il runtime non richiede nuove dipendenze.
Per rigenerarli dopo `npm ci`, usare Python con `fonttools==4.68.0`,
`brotli==1.2.1`, `pillow==13.0.0`, `freetype-py==2.5.1`, quindi:

```sh
python tools/build-amber-fonts.py
```

## Movimento e accessibilità

Quattro durate: 80, 160, 320 e 640 ms. Tutte le curve sono `steps()`.
Animazioni CSS e Web Animations API modificano soltanto `transform` e `opacity`.
I colpi usano spostamenti interi, senza zoom o filtri animati; le animazioni
precedenti vengono cancellate al cambio di evento. Le barre HP/XP aggiornano
una superficie interna tramite `scaleX`; tracce, numeri e attributi ARIA restano invariati.
Il segmento HP ritardato dura 640 ms, con attesa di 160 ms.

Il focus usa un contorno visibile, indipendente dalle ombre dei componenti.
`prefers-reduced-motion` elimina gli effetti di battaglia e i ritardi delle
barre. Texture e scanline sono sovrapposizioni statiche, escluse automaticamente
con `prefers-contrast: more`, `forced-colors` e stampa. Sono disattivabili anche
con `data-atmosphere="off"` su `html` oppure `--atmosphere-opacity: 0`.
Non è stato aggiunto un flusso di impostazioni.

## Perimetro e verifica

Riferimento: commit `282f4496006e93124b9a0cef0b6699d4684fddac`.

- 1.780 dichiarazioni di layout statico conservate, compresi breakpoint,
  griglie, spaziature, dimensioni e metriche tipografiche CSS.
- 36 schermate confrontate in Chrome, a 1440 × 1000 e 390 × 844:
  nessuno scarto nei rettangoli degli elementi a riposo.
- Dialoghi confrontati separatamente con le animazioni disabilitate:
  posizione e dimensioni identiche a entrambe le risoluzioni.
- 1.900 frammenti di testo verificati su 22 viste desktop/mobile:
  nessun contrasto inferiore alla soglia AA applicabile.
- Verificati login da tastiera, switch e attacco, menu del log, focus,
  segmento HP ritardato, notifiche, conferma nativa, movimento ridotto e
  disattivazione delle texture. Nessun errore JavaScript nel browser.
- `npm run build`, `npm run lint` e controllo del diff superati.

DOM dei componenti, semantica, stato, regole, API e navigazione restano quelli
esistenti. Il reskin aggiunge soltanto riferimenti per animazioni imperative,
attributi grafici e il caricamento delle facce tipografiche.

Tre richieste incontrano il vincolo prioritario e restano esplicitamente limitate:

1. Il testo dei dialoghi mantiene la rivelazione per evento già presente.
   Il typewriter per carattere richiederebbe nuovi nodi o un’altra tecnica di
   mascheratura; è sostituito da un breve passaggio di opacità per evento.
2. La chiusura di menu, notifiche e dialoghi segue la rimozione/visibilità già
   esistente. Non viene ritardata per aggiungere un’animazione d’uscita.
3. Gli sprite conservano tutti i box e le scale già richiesti dai componenti,
   incluse le scale frazionarie. Restano `image-rendering: pixelated`; imporre
   multipli interi in ogni istanza cambierebbe dimensioni o proporzioni.
