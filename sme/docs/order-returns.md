# Retours Bob Go et remboursements Paystack

Appliquer `sql/V15__order_returns.sql` avant déploiement.
Toutes les routes exigent le propriétaire authentifié du workspace.
Base : `/api/v1/workspaces/{workspaceId}/orders/{orderId}/return`.

Version actuelle : retour complet d'une commande `FULFILLED`, payée, expédiée avec Bob Go. Un seul retour par commande. Remboursement intégral du paiement Paystack réussi, livraison initiale comprise. Les frais du retour sont supportés par le compte Bob Go du marchand, sans déduction du remboursement. Aucun retour partiel.

1. `POST /quote` :

```json
{
  "reason": "Produit endommagé",
  "merchantContactName": "Service retours",
  "merchantContactEmail": "retours@example.com",
  "merchantContactPhone": "+27821234567",
  "parcels": [{"description":"Commande complète","lengthCm":30,"widthCm":20,"heightCm":10,"weightKg":2}]
}
```

L'adresse client devient l'enlèvement, l'adresse d'expédition du marchand devient la destination. Les coordonnées réelles du marchand et les mesures du colis sont obligatoires. Réponse `ApiResponse` avec `options` et montants en centimes.

2. `POST /shipment` : `{"optionId":"<id reçu dans options>"}`. Devis valable 15 minutes ; seules ses options sont acceptées. La réponse expose `trackingReference` et `shipmentId` pour le suivi/bordereau Bob Go. Le backend crée des expéditions autonomes : le retour utilise `POST /shipments` en sens inverse. `/orders/return` exigerait un ID de commande Bob Go absent de l'intégration actuelle.
3. `POST /receive`, sans corps, après réception physique et inspection par le marchand.
4. `POST /refund`, sans corps : remboursement du paiement d'origine en centimes. `PENDING` ou `PROCESSING` ne signifient pas que le remboursement est terminé.
5. `POST /refund/refresh`, sans corps, pour consulter Paystack. Seul `PROCESSED` met le paiement de la commande à `REFUNDED`. Le frontend doit rafraîchir jusqu'à un résultat final ; cette version ne consomme pas les webhooks de remboursement.
6. `GET` sur la base : état enregistré du retour. Le statut de préparation de la commande reste `FULFILLED` ; le retour possède son propre cycle.

Pas de réintégration automatique du stock : ajuster le stock après inspection via le processus marchand existant.

## Doublons et rapprochement

Un verrou SQL sur la commande sérialise les opérations. `SUBMITTING` est validé en base avant l'appel externe. Répéter `/shipment` ou `/refund` renvoie l'état existant. Les refus, timeouts et réponses incomplètes conduisent à `UNKNOWN`. Une interruption du processus peut laisser `SUBMITTING`.

Ne jamais réinitialiser automatiquement `UNKNOWN`/`SUBMITTING`. Un opérateur doit vérifier le prestataire à partir de la référence de paiement ou de `custom_order_number=RETURN-<orderNumber>`, puis rapprocher et réparer l'enregistrement après vérification. Une répétition aveugle peut doubler une opération. `FAILED`/`NEEDS_ATTENTION` nécessitent également une intervention Paystack ; aucun nouveau remboursement n'est envoyé automatiquement.

Les tests simulent les prestataires. Valider en sandbox avant production. Aucun remboursement réel ni réservation de transport n'a été effectué pendant le développement.

Sources :
- https://api-docs.bob.co.za/bobgo
- https://api-docs.bob.co.za/postman-collections/bobgo.postman_collection.json
- https://paystack.com/docs/api/refund/
