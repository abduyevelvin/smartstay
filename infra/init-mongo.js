// MongoDB initialization script
// Creates dedicated databases and users for hotel-service and review-service.
// Credentials are injected via environment variables from docker-compose.yml.

const hotelUser     = process.env.MONGO_HOTEL_USER     || 'smartstay_hotel';
const hotelPassword = process.env.MONGO_HOTEL_PASSWORD || 'change_me_hotel';
const reviewUser    = process.env.MONGO_REVIEW_USER    || 'smartstay_review';
const reviewPassword = process.env.MONGO_REVIEW_PASSWORD || 'change_me_review';

db = db.getSiblingDB('hotel_db');
db.createUser({
  user: hotelUser,
  pwd:  hotelPassword,
  roles: [{ role: 'readWrite', db: 'hotel_db' }]
});
db.createCollection('hotels');
db.createCollection('rooms');

db = db.getSiblingDB('review_db');
db.createUser({
  user: reviewUser,
  pwd:  reviewPassword,
  roles: [{ role: 'readWrite', db: 'review_db' }]
});
db.createCollection('reviews');
