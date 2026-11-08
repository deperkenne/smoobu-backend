CLAUDE.md — Back-end D&D Clean
Le projet

D&D Clean est une entreprise de nettoyage à Nürnberg (Airbnb, hôtels, bureaux, fin de chantier, voitures, canapés). Ce back-end est son outil interne : il reçoit les réservations des logements des propriétaires, crée les ménages, les propose aux agents et suit leur exécution. Il sert un front React (dashboard secrétaire, interface agent).

Stack
Java 25 ou 21 ou 24, Spring Boot 3 ou 4.1, Maven (./mvnw)
PostgreSQL, Spring Data JPA / Hibernate
Sécurité : Spring Security en resource server OAuth2 / OIDC (Keycloak)
Tests : JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers (PostgreSQL)
Commandes
Lancer : ./mvnw spring-boot:run
Tous les tests : ./mvnw test
Un test : ./mvnw test -Dtest=NomDuTest
Schéma de base (sans Flyway pour l'instant)
En développement, Hibernate crée et met à jour les tables : spring.jpa.hibernate.ddl-auto=update.
Les entités JPA sont donc la seule source du schéma : contraintes (nullable, unique, longueurs) déclarées dans les annotations.
Ne jamais utiliser ddl-auto=update en production. Un outil de migration sera ajouté avant la mise en production : ne pas l'introduire sans demande.
Architecture

Monolithe modulaire, un package par module métier :

de.ddclean
├── owners/         Owner
├── properties/     Property
├── reservations/   Reservation, lecture des e-mails Smoobu

Dans chaque module : XxxController → XxxService → XxxRepository, plus les DTO (record) dans un sous-package dto.

Règles du modèle (décidées)
Customer (compte Keycloak, id = sub du token) → Owner → Property → Reservation → CleaningJob. Un customer peut avoir plusieurs owners ; le customer est lu dans le token, jamais dans le JSON. La réservation garde aussi l'id du customer.
Relations en @ManyToOne(fetch = LAZY) unidirectionnel, côté enfant uniquement. Pas de @OneToMany ni de cascade de suppression. Lister les enfants via le repository.
Owner ◆── Property est une composition dans le modèle, mais on ne supprime jamais : un propriétaire est désactivé (active = false) pour garder l'historique.
Un propriétaire peut être créé avec ses logements en un seul appel et une seule transaction.
Les réservations arrivent par e-mail Smoobu (pas d'API Smoobu pour l'instant). Le logement est retrouvé par Property.emailLabel (nom exact dans l'e-mail) ; la réservation est dédoublonnée par bookingReference. E-mail illisible ou logement inconnu → liste « À classer », jamais perdu.
Ne pas enregistrer le nom du voyageur (RGPD) : il ne sert pas au ménage.
Règles de code
Les contrôleurs reçoivent et renvoient des DTO, jamais des entités.
Validation des entrées avec @Valid ; erreurs centralisées dans un @RestControllerAdvice.
Contrôle des rôles avec @PreAuthorize sur chaque endpoint.
Entités : constructeur protégé pour JPA, méthodes de fabrique (create(...)), modifications par des méthodes métier, pas de setters publics.
Jamais d'appel HTTP ou de lecture de boîte mail dans une transaction : lire d'abord, puis écrire dans une transaction séparée.
@Transactional sur des beans distincts (pas d'auto-appel dans la même classe).
Exceptions métier héritant de RuntimeException.
SOLID, DRY, code minimal : pas de code « au cas où ».
Workflow des agents

Agents dans .claude/agents/. La session principale orchestre : elle délègue, transmet tout le contexte utile à chaque agent (il repart de zéro) et rend compte.

Situation	Enchaînement
Bug, exception, test qui échoue	debugger → bug-fixer → code-reviewer
Nouvelle fonctionnalité	plan (validation si API ou schéma changent) → code → test-writer → code-reviewer
Nettoyer / restructurer	test-writer si le code n'est pas couvert → refactorer → code-reviewer
Avant un commit	./mvnw test → code-reviewer → proposition de message (Conventional Commits)
Ajouter des tests	test-writer → code-reviewer

Règles :

Un seul agent modifie le code à la fois.
Une étape qui modifie le code n'est finie que si ./mvnw test passe.
Au plus 2 allers-retours entre code-reviewer et l'agent qui corrige ; ensuite, demander.
S'arrêter et demander si : cause d'un bug incertaine, changement d'API publique (routes, JSON), du modèle de données ou de la sécurité.
Jamais de commit ni de push sans demande explicite.
