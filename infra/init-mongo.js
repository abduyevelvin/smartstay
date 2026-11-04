// MongoDB initialization script
// Creates dedicated databases and users for hotel-service and review-service

db = db.getSiblingDB('hotel_db');
db.createUser({
  user: 'smartstay_hotel',
  pwd: 'change_me_hotel',
  roles: [{ role: 'readWrite', db: 'hotel_db' }]
});
db.createCollection('hotels');
db.createCollection('rooms');

db = db.getSiblingDB('review_db');
db.createUser({
  user: 'smartstay_review',
  pwd: 'change_me_review',
  roles: [{ role: 'readWrite', db: 'review_db' }]
});
db.createCollection('reviews');
