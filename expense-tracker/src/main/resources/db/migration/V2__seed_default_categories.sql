-- V2__seed_default_categories.sql
-- Seed global default categories (user_id IS NULL)

-- Default Expense Categories
INSERT INTO categories (user_id, name, type, color, icon) VALUES
(NULL, 'Food', 'EXPENSE', '#EF4444', 'utensils'),
(NULL, 'Groceries', 'EXPENSE', '#F97316', 'shopping-cart'),
(NULL, 'Rent', 'EXPENSE', '#84CC16', 'home'),
(NULL, 'Transport', 'EXPENSE', '#06B6D4', 'bus'),
(NULL, 'Fuel', 'EXPENSE', '#3B82F6', 'fuel'),
(NULL, 'Utilities', 'EXPENSE', '#6366F1', 'zap'),
(NULL, 'Entertainment', 'EXPENSE', '#EC4899', 'film'),
(NULL, 'Shopping', 'EXPENSE', '#F43F5E', 'shopping-bag'),
(NULL, 'Health', 'EXPENSE', '#10B981', 'heart-pulse'),
(NULL, 'Education', 'EXPENSE', '#8B5CF6', 'book-open'),
(NULL, 'Travel', 'EXPENSE', '#14B8A6', 'plane'),
(NULL, 'Other', 'EXPENSE', '#64748B', 'more-horizontal');

-- Default Income Categories
INSERT INTO categories (user_id, name, type, color, icon) VALUES
(NULL, 'Salary', 'INCOME', '#10B981', 'wallet'),
(NULL, 'Business', 'INCOME', '#3B82F6', 'briefcase'),
(NULL, 'Interest', 'INCOME', '#F59E0B', 'trending-up'),
(NULL, 'Gift', 'INCOME', '#EC4899', 'gift'),
(NULL, 'Other', 'INCOME', '#94A3B8', 'circle-dollar-sign');
