INSERT INTO circle (slug, name, description, category, icon, color, owner_id, created_at)
SELECT v.slug, v.name, v.description, v.category, v.icon, v.color,
       u.id, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
FROM (
    VALUES
        ('general', '综合交流', '什么都可以聊的地方，欢迎分享日常、想法和有趣的发现。', '兴趣', 'compass', '#2b54f0'),
        ('anime-games', '动漫游戏', '聊动漫、游戏、角色、剧情以及各种二次元话题。', '兴趣', 'heart', '#e11d6b'),
        ('tech', '科技数码', '开发、AI、硬件、软件和互联网产品交流。', '科技', 'code', '#0e8fb8'),
        ('creative', '创作交流', '分享绘画、写作、摄影以及其他原创作品。', '创作', 'edit', '#7c3aed'),
        ('life', '生活分享', '记录生活，交流美食、旅行、兴趣和日常。', '生活', 'camera', '#059f76')
) AS v(slug,name,description,category,icon,color)
CROSS JOIN LATERAL (
    SELECT id FROM platform_user ORDER BY created_at ASC, id ASC LIMIT 1
) u
WHERE NOT EXISTS (SELECT 1 FROM circle c WHERE c.slug = v.slug);

INSERT INTO circle_member (circle_id, user_id, created_at)
SELECT c.id, c.owner_id, c.created_at
FROM circle c
WHERE c.slug IN ('general','anime-games','tech','creative','life')
  AND NOT EXISTS (
      SELECT 1 FROM circle_member cm
      WHERE cm.circle_id = c.id AND cm.user_id = c.owner_id
  );
