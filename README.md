# CoVoSIO

> Application de covoiturage permettant aux utilisateurs de rechercher et réserver des trajets entre villes françaises effectués par des conducteurs vérifiés, avec carte interactive et système de validation administrateur.

---

## Présentation métier

CoVoSIO répond au besoin de mobilité partagée et sécurisée entre particuliers. L'application met en relation des **conducteurs vérifiés** et des **passagers**, sur des trajets entre villes françaises.

| Acteur | Rôle |
|---|---|
| Utilisateur | Recherche des trajets, réserve une place en tant que passager |
| Conducteur | Propose des trajets, gère ses véhicules et documents |
| Administrateur | Vérifie et valide les dossiers conducteurs |

**Valeur apportée** : sécurité renforcée par la vérification des conducteurs, visualisation des trajets sur carte interactive, et gestion complète du cycle de vie d'une réservation.

---

## Stack technique

| Couche | Technologie | Version |
|---|---|---|
| Frontend | React | 18+ |
| Backend | Spring Boot (Java) | 3.x |
| Base de données | MySQL | 8.x |
| ORM | Spring Data JPA (Hibernate) | — |
| Chiffrement mdp | Hashing (BCrypt via Spring Security) | — |
| Tests backend | JUnit + MockMvc | 5.x |
| Tests frontend | Jest | 29.x |

---

## Prérequis

| Logiciel | Version minimale | Commande de vérification |
|---|---|---|
| Java (JDK) | 17 | `java -version` |
| Maven | 3.8 | `mvn -version` |
| Node.js | 18 | `node -v` |
| npm | 9 | `npm -v` |
| MySQL | 8.0 | `mysql --version` |

---

## Installation pas à pas

### a. Cloner les dépôts

```bash
git clone https://github.com/DevDmCh214/CoVoSIO/backend
git clone https://github.com/DevDmCh214/CoVoSIO/frontend
```

### b. Installer les dépendances

**Backend (Maven) :**

```bash
cd backend
mvn install
```

**Frontend (npm) :**

```bash
cd frontend
npm install
```

### c. Configurer les variables d'environnement

Copier le fichier exemple et le remplir :

```bash
cp .env.example .env
```

**Template complet `.env` (backend) :**

```properties
# Base de données
DB_HOST=localhost
DB_PORT=3306
DB_NAME=covosio
DB_USER=covosio_user
DB_PASSWORD=votre_mot_de_passe

# JWT
JWT_SECRET=votre_secret_jwt
JWT_EXPIRATION_MS=86400000

# Serveur
SERVER_PORT=8080
```

**Template complet `.env` (frontend) :**

```properties
REACT_APP_API_URL=http://localhost:8080/api
REACT_APP_MAP_API_KEY=votre_cle_carte
```

### d. Créer la base de données et l'utilisateur dédié

```sql
CREATE DATABASE covosio CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'covosio_user'@'localhost' IDENTIFIED BY 'votre_mot_de_passe';
GRANT SELECT, INSERT, UPDATE, DELETE ON covosio.* TO 'covosio_user'@'localhost';
FLUSH PRIVILEGES;
```

> L'utilisateur `covosio_user` dispose uniquement des droits nécessaires (pas de DROP, pas de CREATE).

### e. Importer le schéma SQL et les données de test

```bash
mysql -u covosio_user -p covosio < docs/sql/schema.sql
mysql -u covosio_user -p covosio < docs/sql/data_test.sql
```

### f. Hachage des mots de passe

Spring Security applique automatiquement BCrypt à l'inscription. Si vous importez des données existantes en clair, exécutez le script de migration :

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--migrate-passwords"
```

---

## Lancer l'application

**Backend :**

```bash
cd backend
mvn spring-boot:run
# Disponible sur http://localhost:8080
```

**Frontend :**

```bash
cd frontend
npm start
# Disponible sur http://localhost:3000
```

---

## Fonctionnalités

### a. Authentification

L'authentification repose sur **JWT (JSON Web Token)** géré par Spring Security.

1. L'utilisateur soumet son email et mot de passe via `POST /api/auth/login`.
2. Spring Security vérifie les credentials et compare le mot de passe haché (BCrypt).
3. En cas de succès, le serveur retourne un token JWT signé.
4. Le frontend stocke le token et l'envoie dans chaque requête via l'en-tête `Authorization: Bearer <token>`.
5. Un filtre Spring (`JwtAuthFilter`) valide le token à chaque requête et peuple le contexte de sécurité.
6. Les routes protégées sont gardées par des annotations `@PreAuthorize` ou des règles `SecurityFilterChain`.

```java
// Exemple de filtre JWT
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && jwtService.isValid(token)) {
            Authentication auth = jwtService.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}
```

---

### b. CRUD complet — Trajets (Trip)

Les conducteurs gèrent leurs trajets via l'API REST suivante :

| Opération | Description | Endpoint HTTP |
|---|---|---|
| Créer | Publier un nouveau trajet | `POST /api/trips` |
| Lire (liste) | Rechercher des trajets disponibles | `GET /api/trips` |
| Lire (détail) | Afficher un trajet spécifique | `GET /api/trips/{id}` |
| Modifier | Mettre à jour un trajet | `PUT /api/trips/{id}` |
| Supprimer | Annuler un trajet | `DELETE /api/trips/{id}` |

---

### c. Association 1,N — Conducteur / Trajets

**Contexte métier :** un conducteur peut proposer plusieurs trajets, mais chaque trajet appartient à un seul conducteur.

```
DRIVER ||--o{ TRIP : "drives"
```

**Modèle JPA :**

```java
// Entité Driver
@Entity
public class Driver {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToMany(mappedBy = "driver", cascade = CascadeType.ALL)
    private List<Trip> trips;
}

// Entité Trip
@Entity
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;
}
```

**Fonctionnalité illustrée :** le tableau de bord du conducteur affiche tous ses trajets filtrés par son identifiant.

```java
// Repository Spring Data JPA
List<Trip> findByDriverId(Integer driverId);
```

**Requête SQL équivalente :**

```sql
SELECT * FROM trip
WHERE driver_id = :driverId
ORDER BY date_time DESC;
```

---

### d. Association N,N — Utilisateur / Trajet via Passenger

**Contexte métier :** un utilisateur peut réserver plusieurs trajets, et un trajet peut accueillir plusieurs passagers. La table pivot `passenger` matérialise cette relation.

**Schéma ASCII de la table pivot :**

```
USER                PASSENGER              TRIP
+----------+       +------------+       +----------+
| id (PK)  |<------| user_id FK |       | id (PK)  |
| name     |       | trip_id FK |------>| driver_id|
| email    |       | id (PK)    |       | date_time|
| password |       +------------+       | status   |
+----------+                            +----------+
```

**Modèle JPA :**

```java
// Entité Passenger (table pivot enrichie)
@Entity
public class Passenger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;
}
```

**Fonctionnalité illustrée :** l'espace "Mes réservations" affiche tous les trajets réservés par l'utilisateur connecté, avec le nombre de passagers par trajet.

```java
// Repository
List<Passenger> findByUserId(Integer userId);
```

**Requête SQL équivalente :**

```sql
SELECT t.*, COUNT(p.id) AS nb_passagers
FROM trip t
JOIN passenger p ON p.trip_id = t.id
WHERE p.user_id = :userId
GROUP BY t.id
ORDER BY t.date_time DESC;
```

---

## Modèle de données

### Tables et rôles

| Table | Rôle |
|---|---|
| `user` | Compte utilisateur standard (passager potentiel) |
| `admin` | Compte administrateur avec niveau de droits |
| `driver` | Extension de `user` pour les conducteurs, statut de vérification |
| `doc` | Documents justificatifs du conducteur (lien chiffré) |
| `car` | Véhicule(s) d'un conducteur |
| `trip` | Trajet proposé par un conducteur avec un véhicule |
| `passenger` | Table pivot : réservation d'un utilisateur sur un trajet |

### Schéma ASCII des relations

```
USER
 |  \
 |   \--- is a ---> DRIVER ---verifies--- ADMIN
 |                    |  \
 |                    |   \--> DOC
 |                    |
 |                    +--> CAR
 |                    |
 |                    +--> TRIP <---used in--- CAR
 |                           |
 \--- books as ---> PASSENGER
```

### Relations

| Relation | Type | Clé étrangère / Table pivot |
|---|---|---|
| USER → DRIVER | 1,1 (héritage) | `driver.user_id` |
| ADMIN → DRIVER | 1,N | `driver.verif_admin_id` |
| DRIVER → DOC | 1,N | `doc.driver_id` |
| DRIVER → CAR | 1,N | `car.driver_id` |
| DRIVER → TRIP | 1,N | `trip.driver_id` |
| CAR → TRIP | 1,N | `trip.car_id` |
| USER ↔ TRIP | N,N | Table pivot `passenger` |

### Diagramme UML

Disponible dans [`docs/uml/`](docs/uml/).

---

## Sécurité

### Chiffrement des mots de passe

Spring Security applique **BCrypt** avec un facteur de coût par défaut (10 rounds), ce qui rend les attaques par force brute coûteuses.

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
}

// Lors de l'inscription
String hashed = passwordEncoder.encode(rawPassword);
user.setPassword(hashed);

// Lors de la connexion
boolean valid = passwordEncoder.matches(rawPassword, user.getPassword());
```

### Tokens JWT

| Paramètre | Valeur |
|---|---|
| Algorithme | HS256 |
| Durée de vie | 24h (86 400 000 ms) |
| Contenu (claims) | `sub` (userId), `role`, `iat`, `exp` |
| Stockage frontend | `localStorage` (ou `httpOnly cookie` recommandé) |

### Isolation des données

Chaque endpoint vérifie que l'utilisateur connecté accède uniquement à ses propres données.

```java
// Exemple : un conducteur ne peut modifier que ses propres trajets
@PutMapping("/trips/{id}")
public ResponseEntity<?> updateTrip(@PathVariable Integer id,
                                    Authentication auth) {
    Trip trip = tripRepository.findById(id).orElseThrow();
    Integer currentUserId = ((UserDetails) auth.getPrincipal()).getId();
    if (!trip.getDriver().getUser().getId().equals(currentUserId)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    // mise à jour...
}
```

### Chiffrement des documents

Les liens vers les documents justificatifs des conducteurs (`doc.link_doc_encrypted`) sont chiffrés en base avant stockage.

### HTTPS — Communication Frontend → Backend

CoVoSIO transmet des données sensibles (credentials, JWT, documents conducteurs) entre React et Spring Boot. Le transport en HTTP clair exposerait ces données à toute écoute réseau. **HTTPS avec TLS 1.2 minimum** est donc obligatoire en production.

**Architecture retenue : reverse proxy Nginx devant Spring Boot**

```
Navigateur (React)
      |
      | HTTPS (TLS 1.3)  port 443
      v
   Nginx (reverse proxy)          ← gère le certificat TLS
      |
      | HTTP local  port 8080     ← réseau interne uniquement
      v
  Spring Boot
```

Nginx porte le certificat et déchiffre TLS. Spring Boot reste en HTTP simple sur le réseau local de la machine — il n'est jamais exposé directement à l'extérieur. C'est l'approche standard, plus simple à maintenir qu'un keystore Java.

**Configuration Nginx minimale :**

```nginx
server {
    listen 443 ssl;
    server_name covosio.example.com;

    ssl_certificate     /etc/letsencrypt/live/covosio.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/covosio.example.com/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;

    location /api/ {
        proxy_pass         http://localhost:8080;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-Proto https;
    }
}

# Redirection HTTP → HTTPS
server {
    listen 80;
    server_name covosio.example.com;
    return 301 https://$host$request_uri;
}
```

**Certificat :** généré gratuitement via **Let's Encrypt / Certbot** et renouvelé automatiquement tous les 90 jours.

```bash
certbot --nginx -d covosio.example.com
```

**Impact côté frontend :** une seule variable d'environnement à mettre à jour.

```properties
# .env (production)
REACT_APP_API_URL=https://covosio.example.com/api
```

| Paramètre | Valeur retenue |
|---|---|
| Protocole minimum | TLS 1.2 |
| Protocole préféré | TLS 1.3 |
| Certificat | Let's Encrypt (gratuit, auto-renouvelé) |
| Redirection HTTP | Oui — 301 permanent vers HTTPS |
| En-tête HSTS | `Strict-Transport-Security: max-age=31536000` |

---

### TLS — Communication Backend → MySQL

Spring Boot accède à MySQL pour toutes les opérations de données (trajets, réservations, conducteurs). Sans chiffrement, les requêtes SQL et leurs résultats transitent en clair sur le réseau — un risque si MySQL est hébergé sur un serveur distant.

**Architecture retenue : MySQL avec `require_secure_transport`**

```
Spring Boot (port 8080)
      |
      | TLS (certificat MySQL auto-signé ou CA)
      v
  MySQL 8 (port 3306)
```

**Étape 1 — Activer SSL côté MySQL 8**

MySQL 8 génère ses certificats SSL automatiquement au démarrage. Vérifier qu'ils sont bien actifs :

```sql
SHOW VARIABLES LIKE '%ssl%';
-- have_ssl doit valoir YES
```

Forcer TLS pour l'utilisateur applicatif :

```sql
ALTER USER 'covosio_user'@'%' REQUIRE SSL;
FLUSH PRIVILEGES;
```

**Étape 2 — Configurer Spring Boot / JDBC**

```properties
# application.properties (production)
spring.datasource.url=jdbc:mysql://db.covosio.example.com:3306/covosio\
  ?useSSL=true\
  &requireSSL=true\
  &verifyServerCertificate=true\
  &trustCertificateKeyStoreUrl=file:/app/certs/mysql-truststore.jks\
  &trustCertificateKeyStorePassword=votre_truststore_password
```

Pour un environnement interne où MySQL est sur la **même machine** que Spring Boot, on peut simplifier en autorisant le certificat auto-signé :

```properties
# application.properties (même machine — réseau loopback)
spring.datasource.url=jdbc:mysql://localhost:3306/covosio\
  ?useSSL=true\
  &requireSSL=true\
  &verifyServerCertificate=false
```

> `verifyServerCertificate=false` est acceptable uniquement si MySQL et Spring Boot tournent sur le même hôte (connexion via `localhost` / `127.0.0.1`). En réseau distant, utiliser un vrai truststore.

| Paramètre | Valeur retenue |
|---|---|
| Chiffrement | TLS (SSL MySQL 8 natif) |
| `require_secure_transport` | `ON` (rejet des connexions non chiffrées) |
| Certificat MySQL | Auto-généré par MySQL 8 au démarrage |
| Vérification certificat | `false` si même machine, `true` si réseau distant |
| Port MySQL | 3306 (non exposé publiquement — firewall) |

**Règle firewall complémentaire :** le port 3306 ne doit jamais être ouvert sur l'interface publique du serveur.

```bash
# Exemple UFW — autoriser MySQL uniquement depuis localhost
ufw deny 3306
ufw allow from 127.0.0.1 to any port 3306
```

---

### Audit / Logs

Spring Boot génère des logs via **SLF4J / Logback**. Les événements d'authentification (succès, échec) et les accès refusés sont tracés dans les logs applicatifs.

---

## Tests

### Lancer les tests

**Backend (JUnit + MockMvc) :**

```bash
# Lancer tous les tests
mvn test

# Avec rapport de couverture (JaCoCo)
mvn verify

# Rapport HTML disponible dans :
# target/site/jacoco/index.html
```

**Frontend (Jest) :**

```bash
# Lancer tous les tests
npm test

# Avec couverture
npm test -- --coverage
```

### Seuil de couverture

Le projet vise un seuil de **70 % de couverture** sur le backend. En dessous de ce seuil, la phase `mvn verify` échoue et bloque le build.

### Scénarios de tests backend

| Fichier de test | Scénarios couverts | HTTP attendu |
|---|---|---|
| `AuthControllerTest` | Login avec credentials valides | 200 OK |
| `AuthControllerTest` | Login avec mauvais mot de passe | 401 Unauthorized |
| `AuthControllerTest` | Login avec email inexistant | 401 Unauthorized |
| `TripControllerTest` | Créer un trajet (conducteur authentifié) | 201 Created |
| `TripControllerTest` | Créer un trajet sans token | 403 Forbidden |
| `TripControllerTest` | Récupérer la liste des trajets | 200 OK |
| `TripControllerTest` | Récupérer un trajet inexistant | 404 Not Found |
| `TripControllerTest` | Modifier un trajet (propriétaire) | 200 OK |
| `TripControllerTest` | Modifier un trajet (non propriétaire) | 403 Forbidden |
| `PassengerControllerTest` | Réserver une place sur un trajet | 201 Created |
| `PassengerControllerTest` | Réserver sur un trajet complet | 409 Conflict |
| `DriverControllerTest` | Soumettre un dossier conducteur | 201 Created |
| `AdminControllerTest` | Valider un dossier conducteur | 200 OK |
| `AdminControllerTest` | Valider sans droits admin | 403 Forbidden |

### Tests frontend (Jest)

| Fichier de test | Scénarios couverts |
|---|---|
| `LoginForm.test.js` | Affichage du formulaire, soumission valide, message d'erreur |
| `TripCard.test.js` | Rendu correct des données d'un trajet |
| `TripSearch.test.js` | Filtrage des résultats par ville de départ |

### Pipeline CI/CD

Aucun pipeline GitHub Actions configuré à ce jour. Intégration prévue en phase ultérieure.

---

## Accessibilité WCAG 2.1 AA

CoVoSIO respecte les critères d'accessibilité WCAG 2.1 niveau AA.

| Critère | Implémentation concrète |
|---|---|
| Contrastes (1.4.3) | Ratio minimum 4,5:1 sur tous les textes, vérifié avec l'outil Contrast Checker |
| Textes alternatifs (1.1.1) | Attribut `alt` renseigné sur toutes les images (`<img alt="Carte du trajet Paris → Lyon" />`) |
| Navigation clavier (2.1.1) | Tous les éléments interactifs accessibles via `Tab`, `Enter`, `Escape` |
| Labels de formulaire (1.3.1) | Chaque champ possède un `<label>` associé via `htmlFor` ou `aria-label` |
| Zones live (4.1.3) | Les notifications (confirmation de réservation, erreurs) utilisent `aria-live="polite"` |
| Focus visible (2.4.7) | Outline CSS visible sur tous les éléments focusables, jamais supprimé sans alternative |
| Hiérarchie des titres (1.3.1) | Structure `h1 > h2 > h3` cohérente sur toutes les pages |
| Carte interactive | Contrôles alternatifs disponibles (liste textuelle des trajets) pour les utilisateurs ne pouvant pas utiliser la carte |
---

## Structure du dépôt

### Backend (`backend`)

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/covosio/
│   │   │   ├── controller/        # Contrôleurs REST (Auth, Trip, Driver, Admin...)
│   │   │   ├── service/           # Logique métier
│   │   │   ├── repository/        # Interfaces Spring Data JPA
│   │   │   ├── model/             # Entités JPA (User, Driver, Trip, Passenger...)
│   │   │   ├── security/          # JWT, filtres Spring Security
│   │   │   └── dto/               # Objets de transfert de données
│   │   └── resources/
│   │       ├── application.properties  # Configuration Spring Boot
│   │       └── application.yml
│   └── test/
│       └── java/com/covosio/      # Tests JUnit + MockMvc
├── docs/
│   ├── sql/
│   │   ├── schema.sql             # Schéma de la base de données
│   │   └── data_test.sql          # Données de test
│   └── uml/                       # Diagrammes UML
├── pom.xml                        # Dépendances Maven
└── README.md
```

### Frontend (`frontend`)

```
frontend/
├── public/                        # Fichiers statiques
├── src/
│   ├── components/                # Composants React réutilisables
│   │   ├── TripCard/
│   │   ├── TripSearch/
│   │   └── MapView/               # Carte interactive
│   ├── pages/                     # Pages de l'application
│   │   ├── Login.jsx
│   │   ├── Dashboard.jsx
│   │   ├── TripSearch.jsx
│   │   └── DriverProfile.jsx
│   ├── services/                  # Appels API (axios)
│   ├── context/                   # Contexte Auth (JWT)
│   ├── __tests__/                 # Tests Jest
│   └── App.jsx
├── .env.example                   # Template des variables d'environnement
├── package.json
└── README.md
```
