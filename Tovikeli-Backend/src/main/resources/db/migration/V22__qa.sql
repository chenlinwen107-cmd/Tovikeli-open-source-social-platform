CREATE TABLE qa_question (id TEXT PRIMARY KEY REFERENCES content (id) ON DELETE RESTRICT,title TEXT NOT NULL,category TEXT NOT NULL,created_at BIGINT NOT NULL);
CREATE INDEX idx_qa_question_category_latest ON qa_question (category, created_at DESC, id DESC);
CREATE TABLE qa_answer (id TEXT PRIMARY KEY REFERENCES content (id) ON DELETE RESTRICT,question_id TEXT NOT NULL REFERENCES qa_question (id) ON DELETE RESTRICT,created_at BIGINT NOT NULL);
CREATE INDEX idx_qa_answer_question_latest ON qa_answer (question_id, created_at DESC, id DESC);
CREATE TABLE qa_acceptance (question_id TEXT PRIMARY KEY REFERENCES qa_question (id) ON DELETE RESTRICT,answer_id TEXT NOT NULL UNIQUE REFERENCES qa_answer (id) ON DELETE RESTRICT,accepted_at BIGINT NOT NULL);