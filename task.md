NOTE: commands are in local via a docker cluster, not in remote server. Check docker compose and README.md

## Partie 2 : Course des tortues

Pour cette partie du TP, nous considérons une pseudo piste d'athlétisme. Cette piste est composée de couloirs fragmentés en cellule. Chaque couloir est composée d'un nombre de cellules numérotées. Cette piste est le terrain d'une course des tortues. Une tortue ne peut que rester sur la cellule où elle se trouve ou avancer d'une ou plusieurs cellules.
L'évolution de la course est décrite par un flux de données dont le schéma est (id, top, tour, cellule, total, maxcel) avec:

-   **id** un entier qui identifie la tortue,
-   **top** un entier qui indique le numéro d'observation des tortues sur la piste,
-   **tour** un entier qui indique le nombre de tour déjà effectué par la tortue,
-   **cellule** un entier qui correspond à la cellule courante où se trouve la tortue (Attention, la position ne permet pas de déterminer le classement de la tortue, car la piste est circulaire et qu'une tortue peut avoir au moins un tour d'avance),
-   **total** indique le nombre total de tortues participant à la course,
-   **maxcel** indique le nombre de cellules constituant la piste.

**Remarque importante**: pour ne pas gaspiller les ressources (CPU, RAM) de votre VM, et sauf contre indication, il sera important de stopper les topologies précédentes avant de lancer une nouvelle topologie (cf bouton KILL sur l'interface UI de STORM) : Mettre 0 comme valeur dans le dialogue et valider pour arrêter la topologie immédiatement.

#### Lancer une course

`./startStream.sh tortoise 10 145 9001` dans le répertoire `ggmd-storm-stream`.

#### Filtrer une tortue

Il s'agit ici d'implémenter un opérateur _stateless_.
Définir un bolt, nommé "MyTortoiseBolt" qui récupère dans le flux d'une tortue (par exemple, la tortue d'id qui vaut 3). Les tuples retournés par ce bolt ont pour schéma :
`(id, top, nom, nbCellsParcourus, total, maxcel)` où :

-   _nom_ correspond à un nom de tortue que vous aurez choisi (ex: Caroline, Donatello, Raphaelo et Michelangelo, Gamera ...
-   _nbCellsParcourus_ correspond au nombre de cellules parcourues par la tortue depuis le début de la courses.

A partir de _ExitBolt_, créer le bolt _Exit2Bolt_ qui prend en entrée des tuples de schéma (id, top, nom, nbCellsParcourus, total, maxcel) et qui produit en sortie un tuple de schéma (json) dont la valeur retournée correspond l'objet JSON attendu.

Définir la topologie TopologyT2, qui permettra de tester votre bolt "MyTortoiseBolt" avec _InputStreamSpout_ et _Exit2Bolt_.

Tester votre topologie TopologyT2 (après avoir arrêté votre topologie TopologyT1).

#### Calcul du rang

Il s'agit ici d'implémenter un opérateur _stateless_.
Définir un bolt, nommé "GiveRankBolt" qui détermine le classement des tortues sur la piste. Les tuples retournés pour chaque tortue par ce bolt ont pour schéma
`(id, top, rang, total, maxcel)` où _rang_ correspond à la chaîne de caractère indiquant le rang de la tortue. En cas d'égalité, le rang des tortues _exae quo_ sera suffixé par le mot 'ex'.

A partir de _ExitBolt_, créer le bolt _Exit3Bolt_ qui prend en entrée des tuples de schéma (id, top, rang, total, maxcel) et qui produit en sortie un tuple de schéma (json) dont la valeur retournée correspond l'objet JSON attendu.

Définir la topologie TopologyT3, qui permettra de tester votre bolt "GiveRankBolt" avec _InputStreamSpout_ et _Exit3Bolt_.

Tester votre topologie TopologyT3 (après avoir arrêté votre topologie TopologyT2).

#### Affectation des points bonus

Il s'agit ici d'implémenter un opérateur _stateful_.
Définir un bolt, nommé "ComputeBonusBolt" qui calcule le nombre de points bonus cumulés par votre tortue. Les tuples retournés par ce bolt ont pour schéma (id, tops, score) où :

-   **tops** une chaîne de caractères de la forme "t<sub>i</sub>-t<sub>i+9</sub>" où t<sub>i</sub> et t<sub>i+9</sub> correspondent respectivement au premier et au dernier top considérés dans le calcul.
-   _score_ est un entier correspondant au score de votre tortue. L’affectation des points bonus se fait le de la manière suivante : tous les 15 tops, le classement de la tortues est transformé en point correspondant au nombre total de participants moins le rang dans le classement. Ainsi, pour 10 participants, le ou les premiers auront 9 points supplémentaires, le ou les seconds auront 8 points supplémentaires et ainsi de suite.

A partir de _ExitBolt_, créer le bolt _Exit4Bolt_ qui prend en entrée des tuples de schéma (id, top, nom, points) et qui produit en sortie un tuple de schéma (json) dont la valeur retournée correspond l'objet JSON attendu.

Définir la topologie TopologyT4, qui permettra de tester votre bolt "ComputeBonusBolt" avec _InputStreamSpout_, _MyTortoiseBolt_, _GiveRankBolt_ et _Exit4Bolt_.

Tester votre topologie TopologyT4 (après avoir arrêté votre topologie TopologyT3).

#### Vitesse moyenne

Il s'agit ici d'implémenter un opérateur _stateless_ avec fenêtrage.
Définir un bolt, nommé "SpeedBolt, qui détermine la vitesse moyenne de la tortue exprimée en cellule par top calculée sur 10 tops et ce, tous les 5 tuples reçus. Les tuples retournés par ce bolt ont pour schéma (id, nom, tops, vitesse), où :

-   **tops** une chaîne de caractères de la forme "t<sub>i</sub>-t<sub>i+9</sub>" où t<sub>i</sub> et t<sub>i+9</sub> correspondent respectivement au premier et au dernier top considérés dans le calcul.
-   **vitesse** un décimal correspondant au nombre cellules parcourues sur le nombre de tops correspondant.
-   A partir de _ExitBolt_, créer le bolt _Exit5Bolt_ qui prend en entrée des tuples de schéma (id, top, nom, vitesse) et qui produit en sortie un tuple de schéma (json) dont la valeur retournée correspond l'objet JSON attendu.

Définir la topologie TopologyT5, qui permettra de tester votre bolt "SpeedBolt" avec _InputStreamSpout_ et _Exit5Bolt_.

Tester votre topologie TopologyT5 (après avoir arrêté votre topologie TopologyT4).

#### Evolution du rang

Il s'agit ici d'implémenter un opérateur _stateful_ avec fenêtrage.
Définir un bolt, nommé "RankEvolutionBolt", qui détermine l'évolution du rang ("En progression", "Constant" ou "En régression") de votre tortue sur une fenêtre de 10 secondes. Les tuples retournés par ce bolt ont pour schéma (id, nom, date, evolution) où :

-   **date** une chaîne de caractères correspondant au **timestamp** du calcul de l'évolution de l'évolution de la vitesse.
-   **evolution** correspond à la chaîne de caractères indiquant si la tortue est :
    -   en progression, pour traduire qu'il a gagné au moins une place dans le classement au bout de 30 secondes
    -   constant, pour traduire qu'il a la même place au bout de 30 secondes
    -   en régression, pour traduire qu'il a perdu au moins une place dans le classement au bout de 30 secondes.

A partir de _ExitBolt_, créer le bolt _Exit6Bolt_ qui prend en entrée des tuples de schéma (id, top, nom, points) et qui produit en sortie un tuple de schéma (json) dont la valeur retournée correspond l'objet JSON attendu.

Définir la topologie TopologyT6, qui permettra de tester votre bolt "RankEvolutionBolt" avec _InputStreamSpout_, _MyTortoiseBolt_, _GiveRankBolt_ et _Exit6Bolt_.

Tester votre topologie TopologyT6 (après avoir arrêté votre topologie TopologyT5).

## Partie 3 : Course des lièvres

Pour cette partie du TP, nous conservons le contexte de course mais cette fois les tortues sont remplacées par des lapins.
Dans ce contexte, les observations sont bien plus nombreuses et fréquentes.
Dans la partie précédente, les ressources étaient suffisantes pour absorber le débit du flux. Dans cette partie, ce ne sera plus le cas.
Certains opérateurs vont se retrouver dans un état de congestion, c'est-à-dire que le nombre de tuples à traiter est plus important que le nombre de tuples qu'il est en capacité de traiter.
Pour résoudre ce problème, vous allez dans un premier temps modifier le degré de parallélisme de l'opérateur congestionné pour avoir plusieurs _thread_ et ainsi exécuter l'opérateur en parallèle.
Ensuite, vous évaluerez l'impact d'ajouter un _supervisor_ supplémentaire.

### Flux d'entrée

Pour lancer le flux d'entrée, vous utiliserez à nouveau le script `./startStream.sh` dans le répertoire `ggmd-storm-stream`.

Cette fois, vous lancerez sur le port 9002 une course de 1000 lièvre sur un piste circulaire de 145 cellules. Ce qui donnera la commande :

```
	./startStream.sh rabbit 1000 145 9002
```

### Crypter les données

L'objectif est de crypter les données qui vont être reçu en nombre.
L'opérateur de cryptage devrait permettre de générer des latences qui amèneront le système à atteindre ses limites.
Les classes nécessaires sont fournies :

-   pour le bolt de traitement, utiliser _ConsumeTimeBolt_ .
-   pour le bolt de clôture, utiliser _ExitInLogBolt_.
-   pour la topologie, utiliser _TopologyE1_
-   Lancer la topologie TopologyE1.

En regardant dans l'**ui** l'affichage de votre topologie, que remarquez-vous sur l'état du noeud correspondant au bolt "ComputePodiumBolt" (si vous ne voyez rien d'anormal... attendez un peu! Des 'failed' devraient apparaître).

### Déphasage des tuples

Pour tester les performances de notre bolt "ComputePodiumBolt", nous allons calculer le nombre de tuples déphasés (i.e. qui n'ont pas pu être traité dans le temps imparti).
Pour cela, ajouter une instruction dans la méthode **fail** du spout pour pouvoir journaliser un message du type:

`"[ConsumeTime] Failure (msg: x ) after y seconds"`

avec x le numéro du message source de l'échec et y le temps en seconde depuis le lancement de la topologie.

Vous pourrez récupérer l'information via un grep dans les différents fichiers worker.log des répertoires 6700, 6701, 6702 et 6703 dans logs/workers-artifacts/ttt où ttt correspond au nom de votre topologie.

Par la suite, nous partons du principe que la topologie est congestionné à partir de 10 échecs observés.

En combien de temps votre topologie est congestionnée?

### Gestion de la congestion

#### Modification du parallélisme

Dans un premier temps, nous allons modifier le parallélisme de l'opérateur "ConsumeTimeBolt".
Modifier votre topologie TopologyE1, pour associer 4 workers et 4 Executors à votre opérateur.

Qu'observez-vous?

#### Ajout d'un supervisor distant

Pour finir, configurer le storm pour accéder à la VM de votre binôme.
Pour cela, modifier le conf/storm.yaml du slave et affecter le nom complet de votre VM (stormXXX.novalocal) au _nimbus.seeds_. Il est aussi nécessaire de modifier le _/etc/hosts_ du slave pour déclarer votre VM. Il sera également nécessaire de modifier le /etc/hosts de votre VM pour déclarer le slave.
Lancer un supervisor qui sera connecté à votre nimbus. Vérifier sur l'ui que le nouveau supervisor est bien répertorié dans la section "Summary supervior".

Modifier votre topologie _TopologyE1_ pour qu'elle puissent s'exécuter sur 8 workers (et non plus 4).

Qu'observez-vous?
