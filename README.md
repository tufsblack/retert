# BeneluxPvP – het complete PvP-systeem van Benelux PvP HQ

Eén plugin met: kits, arena's die zichzelf herstellen, wachtrij, vrijwillige duels, meekijken,
statistieken (kills, deaths, K/D, reeks), ranglijsten en Cosmetics-beloningen. Alles werkt met menu's (GUI).
Thema: rood, donkerrood en goud.

## Installeren
1. Bouw de jar (zie onderaan) en zet `BeneluxPvP.jar` in `plugins/` van je **pvp-server** (niet de lobby of survival).
2. Wil je de beloningen? Zet ook **Cosmetics 1.4.1 of nieuwer** in `plugins/` (1.4.0 werkt ook, maar toont dan geen 3D-kronen in de beloningen). Die blijft een aparte plugin. Zonder Cosmetics werkt al het andere gewoon.
3. Start de server. Dit vervangt Duels Optimised: haal die plugin eraf.

## Eerste keer instellen (staff, `/pvpadmin`)
1. **Lobby**: ga staan waar spelers na een gevecht terugkomen en typ `/pvpadmin lobby`.
2. **Kit maken**: `/pvpadmin` > Kits > Nieuwe kit. Leg je inventaris klaar zoals de kit moet zijn; de kit wordt een kopie daarvan.
3. **Arena maken**: `/pvpadmin` > Arena's > Nieuwe arena. Stel hoek 1 en hoek 2 in (het gebied), kies de spawns van speler 1 en 2 en de kijkplek (alle drie binnen het gebied) en druk op **Opslaan origineel**. Die opgeslagen versie komt na elk gevecht terug.
4. Per arena kies je welke kits toegestaan zijn en of spelers er mogen bouwen/breken. Alles wat ze bouwen of slopen wordt na afloop hersteld.
5. Staff-menu: ook voor instellingen (aftellen, tijdslimieten, regels voor tellen), statistieken aanpassen/resetten en beloningen.

## Voor spelers (`/pvp`)
- **Wachtrij**: kies kit + arena (of willekeurig). Zodra iemand anders hetzelfde koos begint het gevecht.
- **Uitdagen**: `/duel <speler> [kit] [arena]` of via het menu. De ander accepteert of weigert met klikbare knoppen (`/duel accept|deny <speler>`). Een uitdaging verloopt na 60 seconden. Je kunt uitdagingen uitzetten in het hoofdmenu.
- **Meekijken**: `/pvp spectate [speler]`.
- **Statistieken**: `/pvpstats [speler]`. **Ranglijst**: `/pvptop [kills|deaths|kd|streak]`.
- **Beloningen**: `/kills` laat zien hoeveel kills je nodig hebt voor elke cosmetic.
- `/pvp leave`: wachtrij verlaten, of tijdens een gevecht opgeven.

## Commando's
| Commando | Wat |
|---|---|
| `/pvp` (`/bpvp`) | hoofdmenu, `queue`, `leave`, `accept`, `deny`, `stats`, `top`, `spectate`, `rewards` |
| `/duel` (`/uitdagen`) | uitdagen |
| `/pvpstats` (`/stats`, `/kd`) | statistieken |
| `/pvptop` (`/lb`, `/ranglijst`) | ranglijst |
| `/kills` (`/beloningen`) | cosmetic-beloningen |
| `/pvpadmin` | staff-menu; ook `status`, `reload`, `lobby`, `eis <cosmetic> <aantal\|uit>`, `zet <speler> <kills\|deaths> <aantal>`, `reset <speler>` |

## Rechten
- `beneluxpvp.use` – spelen (standaard iedereen)
- `beneluxpvp.admin` – staff-menu en `/pvpadmin` (standaard alleen op ops; geef via LuckPerms aan staff)
- `beneluxpvp.bypass` – regels omzeilen (bouwen, commando's in een gevecht)

## Hoe het werkt (veiligheid)
- Voor een gevecht wordt je inventaris, level, honger, locatie en spelmodus **eerst naar schijf** bewaard en daarna pas vervangen. Na het gevecht krijg je alles terug, ook na een crash, afmelden of herstart (bij het inloggen).
- Het gebied is zijwaarts afgesloten (een muur), maar omhoog en omlaag vrij: windcharges, mace-vallen en ender pearls omhoog werken gewoon.
- Genezen: je begint elk gevecht met volle honger maar zonder extra verzadiging, dus je regenereert langzaam (alleen gouden appels helpen echt). Wil je sneller genezen, verhoog dan "Verzadiging" in het staff-menu bij de instellingen.
- Doodgaan in een gevecht is geen echte dood: je gaat naar de kijkplek en de winnaar wordt uitgeroepen. Er valt niets op de grond.
- Arena's worden hersteld in kleine stukjes per tick (geen lag). Is de server tijdens een herstel gecrasht, dan wordt dat na de herstart afgemaakt.
- Kills/deaths tellen alleen als de regels het toestaan: niet zelfde IP, niet dezelfde tegenstander binnen 5 minuten, maximaal 30 per uur. Alles is aan te passen in het staff-menu en `config.yml`.
- Menu's zijn niet te plunderen: elke klik wordt geblokkeerd en menu-items kunnen nooit in een inventaris blijven.

## Bekende beperkingen
- Effecten (potions) die je vóór een gevecht had worden niet teruggezet; ze worden gewist.
- De plugin is uitgebreid getest met een nagemaakte server (646 controles, o.a. gevechten, wachtrij, crash en herstart, duels, statistieken, beloningen en honderden willekeurige menuklikken), maar nog niet op een echte Paper-server. Test dus eerst even met twee accounts en meld alles wat opvalt.

## Op GitHub zetten en bouwen (stap voor stap)
1. Maak op GitHub een nieuwe **publieke** repository en kies **Add file > Upload files**. Sleep alle losse bestanden uit de zip erin (zonder de map `.github`; die doen we in stap 2) en klik **Commit changes**.
2. De bouw-instructie moet in een verborgen map staan, en die laat GitHub vaak niet uploaden. Doe daarom dit: klik **Add file > Create new file**. Typ bij **Name your file...** precies: `.github/workflows/build.yml` (typ je de `/`, dan maakt GitHub de mappen zelf). Open `bouw-workflow-kopie.yml` uit de zip, kopieer alles, plak het in het grote vak en klik **Commit changes**.
3. Dit start de bouw vanzelf. Je ziet hem onder **Actions**. Wil je hem opnieuw starten: **Actions > Build BeneluxPvP > Run workflow**.
4. Is hij klaar (groen vinkje)? Klik de run open, scroll naar **Artifacts** en download de jar. Pak de zip uit en zet de `.jar` in `plugins/`.
5. Rood kruisje? Open de run > **build** > zoek de regels met `[ERROR]` en stuur ze naar Claude.
