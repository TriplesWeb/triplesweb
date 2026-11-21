UPDATE media
SET tab = 'behind'
WHERE tab = 'mv'
  AND LOWER(media_title) LIKE '%reaction%';
