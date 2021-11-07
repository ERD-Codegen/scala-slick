-- system columns triggers
CREATE OR REPLACE FUNCTION trigger_set_creation_date()
RETURNS TRIGGER AS $$
BEGIN
  NEW.creation_date = NOW();
  NEW.last_update_date = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trigger_set_last_update_date()
RETURNS TRIGGER AS $$
BEGIN
  NEW.last_update_date = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE TABLE users(
    user_id BIGSERIAL PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    salt BYTEA NOT NULL,
    hashed_pbkdf2 BYTEA NOT NULL,
    username TEXT NOT NULL UNIQUE,
    bio TEXT,
    image_url TEXT
);


CREATE TABLE articles(
    article_id BIGSERIAL PRIMARY KEY,
    slug TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    body TEXT NOT NULL,
    author BIGINT REFERENCES users (user_id) ON DELETE CASCADE NOT NULL,
    creation_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_update_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TRIGGER set_creation_date
BEFORE INSERT ON articles
FOR EACH ROW
EXECUTE PROCEDURE trigger_set_creation_date();

CREATE TRIGGER set_last_update_date
BEFORE UPDATE ON articles
FOR EACH ROW
EXECUTE PROCEDURE trigger_set_last_update_date();


CREATE TABLE article_tags(
    article_id BIGINT REFERENCES articles (article_id) ON DELETE CASCADE NOT NULL,
    tag TEXT NOT NULL,
    PRIMARY KEY (article_id, tag)
);


CREATE TABLE article_favorites(
    article_id BIGINT REFERENCES articles (article_id) ON DELETE CASCADE NOT NULL,
    user_id BIGINT REFERENCES users (user_id) ON DELETE CASCADE NOT NULL,
    PRIMARY KEY (article_id, user_id)
);


CREATE TABLE article_comments(
    comment_id BIGSERIAL PRIMARY KEY,
    article_id BIGINT REFERENCES articles (article_id) ON DELETE CASCADE NOT NULL,
    body TEXT NOT NULL,
    author BIGINT REFERENCES users (user_id) ON DELETE CASCADE NOT NULL,
    creation_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_update_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);


CREATE TABLE follows(
    master_id BIGINT REFERENCES users (user_id) ON DELETE CASCADE NOT NULL,
    slave_id BIGINT REFERENCES users (user_id) ON DELETE CASCADE NOT NULL,
    PRIMARY KEY (master_id, slave_id)
);