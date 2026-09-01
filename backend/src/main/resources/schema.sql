-- ============================================================
-- LeetCode Platform - Full Database Schema
-- ============================================================

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    email       VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    avatar_url  VARCHAR(500),
    bio         VARCHAR(500),
    github_url  VARCHAR(200),
    streak      INT          NOT NULL DEFAULT 0,
    last_active DATE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Tags table
CREATE TABLE IF NOT EXISTS tags (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- Problems table
CREATE TABLE IF NOT EXISTS problems (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    slug         VARCHAR(200) NOT NULL UNIQUE,
    description  TEXT         NOT NULL,
    difficulty   VARCHAR(10)  NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    constraints  TEXT,
    starter_code TEXT,
    solution     TEXT,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    acceptance   DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    submissions  INT          NOT NULL DEFAULT 0,
    created_by   BIGINT REFERENCES users(id),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Problem tags junction table
CREATE TABLE IF NOT EXISTS problem_tags (
    problem_id BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    tag_id     BIGINT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (problem_id, tag_id)
);

-- Problem examples table
CREATE TABLE IF NOT EXISTS problem_examples (
    id          BIGSERIAL PRIMARY KEY,
    problem_id  BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    input       TEXT   NOT NULL,
    output      TEXT   NOT NULL,
    explanation TEXT,
    order_index INT    NOT NULL DEFAULT 0
);

-- Test cases table
CREATE TABLE IF NOT EXISTS test_cases (
    id         BIGSERIAL PRIMARY KEY,
    problem_id BIGINT  NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    input      TEXT    NOT NULL,
    expected   TEXT    NOT NULL,
    is_hidden  BOOLEAN NOT NULL DEFAULT FALSE,
    order_index INT    NOT NULL DEFAULT 0
);

-- Submissions table
CREATE TABLE IF NOT EXISTS submissions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    problem_id      BIGINT       NOT NULL REFERENCES problems(id),
    code            TEXT         NOT NULL,
    language        VARCHAR(20)  NOT NULL DEFAULT 'java',
    status          VARCHAR(30)  NOT NULL,
    runtime_ms      INT,
    memory_kb       INT,
    error_message   TEXT,
    test_results    JSONB,
    passed_tests    INT          NOT NULL DEFAULT 0,
    total_tests     INT          NOT NULL DEFAULT 0,
    submitted_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Indices for performance
CREATE INDEX IF NOT EXISTS idx_submissions_user_id     ON submissions(user_id);
CREATE INDEX IF NOT EXISTS idx_submissions_problem_id  ON submissions(problem_id);
CREATE INDEX IF NOT EXISTS idx_submissions_status      ON submissions(status);
CREATE INDEX IF NOT EXISTS idx_problems_difficulty     ON problems(difficulty);
CREATE INDEX IF NOT EXISTS idx_problems_slug           ON problems(slug);
CREATE INDEX IF NOT EXISTS idx_test_cases_problem_id   ON test_cases(problem_id);

-- ============================================================
-- Seed Data
-- ============================================================

-- Insert tags
INSERT INTO tags (name) VALUES
    ('Array'), ('String'), ('Hash Table'), ('Dynamic Programming'),
    ('Math'), ('Sorting'), ('Greedy'), ('Depth-First Search'),
    ('Breadth-First Search'), ('Binary Search'), ('Two Pointers'),
    ('Sliding Window'), ('Stack'), ('Queue'), ('Linked List'),
    ('Tree'), ('Graph'), ('Recursion'), ('Backtracking'), ('Bit Manipulation')
ON CONFLICT (name) DO NOTHING;

-- Insert sample admin user (password: admin123)
INSERT INTO users (username, email, password, role) VALUES
    ('admin', 'admin@leetcode.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Jb1a', 'ADMIN')
ON CONFLICT (username) DO NOTHING;

-- Insert Problems
INSERT INTO problems (title, slug, description, difficulty, constraints, starter_code, solution, created_by) VALUES
(
    'Two Sum',
    'two-sum',
    'Given an array of integers `nums` and an integer `target`, return indices of the two numbers such that they add up to `target`.

You may assume that each input would have exactly one solution, and you may not use the same element twice.

You can return the answer in any order.',
    'EASY',
    '2 <= nums.length <= 10^4
-10^9 <= nums[i] <= 10^9
-10^9 <= target <= 10^9
Only one valid answer exists.',
    'class Solution {
    public int[] twoSum(int[] nums, int target) {
        // Write your solution here
    }
}',
    'class Solution {
    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) {
                return new int[]{map.get(complement), i};
            }
            map.put(nums[i], i);
        }
        return new int[]{};
    }
}',
    1
),
(
    'Reverse String',
    'reverse-string',
    'Write a function that reverses a string. The input string is given as an array of characters `s`.

You must do this by modifying the input array in-place with `O(1)` extra memory.',
    'EASY',
    '1 <= s.length <= 10^5
s[i] is a printable ASCII character.',
    'class Solution {
    public void reverseString(char[] s) {
        // Write your solution here
    }
}',
    'class Solution {
    public void reverseString(char[] s) {
        int left = 0, right = s.length - 1;
        while (left < right) {
            char temp = s[left];
            s[left] = s[right];
            s[right] = temp;
            left++;
            right--;
        }
    }
}',
    1
),
(
    'Valid Parentheses',
    'valid-parentheses',
    'Given a string `s` containing just the characters `(`, `)`, `{`, `}`, `[` and `]`, determine if the input string is valid.

An input string is valid if:
1. Open brackets must be closed by the same type of brackets.
2. Open brackets must be closed in the correct order.
3. Every close bracket has a corresponding open bracket of the same type.',
    'EASY',
    '1 <= s.length <= 10^4
s consists of parentheses only ()[]{}.',
    'class Solution {
    public boolean isValid(String s) {
        // Write your solution here
    }
}',
    'class Solution {
    public boolean isValid(String s) {
        Stack<Character> stack = new Stack<>();
        for (char c : s.toCharArray()) {
            if (c == ''('' || c == ''{'' || c == ''['') {
                stack.push(c);
            } else {
                if (stack.isEmpty()) return false;
                char top = stack.pop();
                if (c == '')'' && top != ''('') return false;
                if (c == ''}'' && top != ''{'') return false;
                if (c == '']'' && top != ''['') return false;
            }
        }
        return stack.isEmpty();
    }
}',
    1
),
(
    'Longest Substring Without Repeating Characters',
    'longest-substring-without-repeating-characters',
    'Given a string `s`, find the length of the **longest substring** without repeating characters.',
    'MEDIUM',
    '0 <= s.length <= 5 * 10^4
s consists of English letters, digits, symbols and spaces.',
    'class Solution {
    public int lengthOfLongestSubstring(String s) {
        // Write your solution here
    }
}',
    'class Solution {
    public int lengthOfLongestSubstring(String s) {
        Map<Character, Integer> map = new HashMap<>();
        int maxLen = 0, left = 0;
        for (int right = 0; right < s.length(); right++) {
            char c = s.charAt(right);
            if (map.containsKey(c)) {
                left = Math.max(left, map.get(c) + 1);
            }
            map.put(c, right);
            maxLen = Math.max(maxLen, right - left + 1);
        }
        return maxLen;
    }
}',
    1
),
(
    'Merge Two Sorted Lists',
    'merge-two-sorted-lists',
    'You are given the heads of two sorted linked lists `list1` and `list2`.

Merge the two lists into one sorted list. The list should be made by splicing together the nodes of the first two lists.

Return the head of the merged linked list.',
    'EASY',
    'The number of nodes in both lists is in the range [0, 50].
-100 <= Node.val <= 100
Both list1 and list2 are sorted in non-decreasing order.',
    'class Solution {
    public ListNode mergeTwoLists(ListNode list1, ListNode list2) {
        // Write your solution here
    }
}',
    'class Solution {
    public ListNode mergeTwoLists(ListNode list1, ListNode list2) {
        ListNode dummy = new ListNode(0);
        ListNode curr = dummy;
        while (list1 != null && list2 != null) {
            if (list1.val <= list2.val) {
                curr.next = list1;
                list1 = list1.next;
            } else {
                curr.next = list2;
                list2 = list2.next;
            }
            curr = curr.next;
        }
        curr.next = (list1 != null) ? list1 : list2;
        return dummy.next;
    }
}',
    1
),
(
    'Maximum Subarray',
    'maximum-subarray',
    'Given an integer array `nums`, find the subarray with the largest sum, and return its sum.',
    'MEDIUM',
    '1 <= nums.length <= 10^5
-10^4 <= nums[i] <= 10^4',
    'class Solution {
    public int maxSubArray(int[] nums) {
        // Write your solution here
    }
}',
    'class Solution {
    public int maxSubArray(int[] nums) {
        int maxSum = nums[0];
        int currentSum = nums[0];
        for (int i = 1; i < nums.length; i++) {
            currentSum = Math.max(nums[i], currentSum + nums[i]);
            maxSum = Math.max(maxSum, currentSum);
        }
        return maxSum;
    }
}',
    1
),
(
    'Climbing Stairs',
    'climbing-stairs',
    'You are climbing a staircase. It takes `n` steps to reach the top.

Each time you can either climb `1` or `2` steps. In how many distinct ways can you climb to the top?',
    'EASY',
    '1 <= n <= 45',
    'class Solution {
    public int climbStairs(int n) {
        // Write your solution here
    }
}',
    'class Solution {
    public int climbStairs(int n) {
        if (n <= 2) return n;
        int a = 1, b = 2;
        for (int i = 3; i <= n; i++) {
            int c = a + b;
            a = b;
            b = c;
        }
        return b;
    }
}',
    1
),
(
    'Binary Search',
    'binary-search',
    'Given an array of integers `nums` which is sorted in ascending order, and an integer `target`, write a function to search `target` in `nums`. If `target` exists, then return its index. Otherwise, return `-1`.

You must write an algorithm with `O(log n)` runtime complexity.',
    'EASY',
    '1 <= nums.length <= 10^4
-10^4 < nums[i], target < 10^4
All the integers in nums are unique.
nums is sorted in ascending order.',
    'class Solution {
    public int search(int[] nums, int target) {
        // Write your solution here
    }
}',
    'class Solution {
    public int search(int[] nums, int target) {
        int left = 0, right = nums.length - 1;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (nums[mid] == target) return mid;
            if (nums[mid] < target) left = mid + 1;
            else right = mid - 1;
        }
        return -1;
    }
}',
    1
),
(
    'Word Search',
    'word-search',
    'Given an `m x n` grid of characters `board` and a string `word`, return `true` if `word` exists in the grid.

The word can be constructed from letters of sequentially adjacent cells, where adjacent cells are horizontally or vertically neighboring. The same letter cell may not be used more than once.',
    'MEDIUM',
    'm == board.length
n = board[i].length
1 <= m, n <= 6
1 <= word.length <= 15
board and word consists of only lowercase and uppercase English letters.',
    'class Solution {
    public boolean exist(char[][] board, String word) {
        // Write your solution here
    }
}',
    'class Solution {
    public boolean exist(char[][] board, String word) {
        for (int i = 0; i < board.length; i++)
            for (int j = 0; j < board[0].length; j++)
                if (dfs(board, word, i, j, 0)) return true;
        return false;
    }
    private boolean dfs(char[][] board, String word, int i, int j, int k) {
        if (k == word.length()) return true;
        if (i < 0 || i >= board.length || j < 0 || j >= board[0].length || board[i][j] != word.charAt(k)) return false;
        char tmp = board[i][j];
        board[i][j] = ''#'';
        boolean found = dfs(board, word, i+1, j, k+1) || dfs(board, word, i-1, j, k+1)
                     || dfs(board, word, i, j+1, k+1) || dfs(board, word, i, j-1, k+1);
        board[i][j] = tmp;
        return found;
    }
}',
    1
),
(
    'Median of Two Sorted Arrays',
    'median-of-two-sorted-arrays',
    'Given two sorted arrays `nums1` and `nums2` of size `m` and `n` respectively, return **the median** of the two sorted arrays.

The overall run time complexity should be `O(log (m+n))`.',
    'HARD',
    'nums1.length == m
nums2.length == n
0 <= m <= 1000
0 <= n <= 1000
1 <= m + n <= 2000
-10^6 <= nums1[i], nums2[i] <= 10^6',
    'class Solution {
    public double findMedianSortedArrays(int[] nums1, int[] nums2) {
        // Write your solution here
    }
}',
    'class Solution {
    public double findMedianSortedArrays(int[] nums1, int[] nums2) {
        if (nums1.length > nums2.length) return findMedianSortedArrays(nums2, nums1);
        int m = nums1.length, n = nums2.length;
        int left = 0, right = m;
        while (left <= right) {
            int partX = (left + right) / 2;
            int partY = (m + n + 1) / 2 - partX;
            int maxLeftX = (partX == 0) ? Integer.MIN_VALUE : nums1[partX - 1];
            int minRightX = (partX == m) ? Integer.MAX_VALUE : nums1[partX];
            int maxLeftY = (partY == 0) ? Integer.MIN_VALUE : nums2[partY - 1];
            int minRightY = (partY == n) ? Integer.MAX_VALUE : nums2[partY];
            if (maxLeftX <= minRightY && maxLeftY <= minRightX) {
                if ((m + n) % 2 == 0)
                    return (Math.max(maxLeftX, maxLeftY) + Math.min(minRightX, minRightY)) / 2.0;
                return Math.max(maxLeftX, maxLeftY);
            } else if (maxLeftX > minRightY) right = partX - 1;
            else left = partX + 1;
        }
        return 0.0;
    }
}',
    1
)
ON CONFLICT (slug) DO NOTHING;

-- Problem examples
INSERT INTO problem_examples (problem_id, input, output, explanation, order_index)
SELECT p.id, '[2,7,11,15], target = 9', '[0,1]', 'Because nums[0] + nums[1] == 9, we return [0, 1].', 0
FROM problems p WHERE p.slug = 'two-sum'
ON CONFLICT DO NOTHING;

INSERT INTO problem_examples (problem_id, input, output, explanation, order_index)
SELECT p.id, '[3,2,4], target = 6', '[1,2]', NULL, 1
FROM problems p WHERE p.slug = 'two-sum'
ON CONFLICT DO NOTHING;

INSERT INTO problem_examples (problem_id, input, output, explanation, order_index)
SELECT p.id, '[''h'',''e'',''l'',''l'',''o'']', '[''o'',''l'',''l'',''e'',''h'']', 'The input string is "hello", the output is "olleh".', 0
FROM problems p WHERE p.slug = 'reverse-string'
ON CONFLICT DO NOTHING;

INSERT INTO problem_examples (problem_id, input, output, explanation, order_index)
SELECT p.id, 's = "()"', 'true', NULL, 0
FROM problems p WHERE p.slug = 'valid-parentheses'
ON CONFLICT DO NOTHING;

INSERT INTO problem_examples (problem_id, input, output, explanation, order_index)
SELECT p.id, 's = "()[]{}"', 'true', NULL, 1
FROM problems p WHERE p.slug = 'valid-parentheses'
ON CONFLICT DO NOTHING;

INSERT INTO problem_examples (problem_id, input, output, explanation, order_index)
SELECT p.id, 's = "(]"', 'false', NULL, 2
FROM problems p WHERE p.slug = 'valid-parentheses'
ON CONFLICT DO NOTHING;

-- Test cases for Two Sum
INSERT INTO test_cases (problem_id, input, expected, is_hidden, order_index)
SELECT p.id, '{"nums": [2, 7, 11, 15], "target": 9}', '[0, 1]', false, 0
FROM problems p WHERE p.slug = 'two-sum'
ON CONFLICT DO NOTHING;

INSERT INTO test_cases (problem_id, input, expected, is_hidden, order_index)
SELECT p.id, '{"nums": [3, 2, 4], "target": 6}', '[1, 2]', false, 1
FROM problems p WHERE p.slug = 'two-sum'
ON CONFLICT DO NOTHING;

INSERT INTO test_cases (problem_id, input, expected, is_hidden, order_index)
SELECT p.id, '{"nums": [3, 3], "target": 6}', '[0, 1]', true, 2
FROM problems p WHERE p.slug = 'two-sum'
ON CONFLICT DO NOTHING;

INSERT INTO test_cases (problem_id, input, expected, is_hidden, order_index)
SELECT p.id, '{"nums": [-1, -2, -3, -4, -5], "target": -8}', '[2, 4]', true, 3
FROM problems p WHERE p.slug = 'two-sum'
ON CONFLICT DO NOTHING;

-- Test cases for Valid Parentheses
INSERT INTO test_cases (problem_id, input, expected, is_hidden, order_index)
SELECT p.id, '{"s": "()"}', 'true', false, 0
FROM problems p WHERE p.slug = 'valid-parentheses'
ON CONFLICT DO NOTHING;

INSERT INTO test_cases (problem_id, input, expected, is_hidden, order_index)
SELECT p.id, '{"s": "()[]{}"}', 'true', false, 1
FROM problems p WHERE p.slug = 'valid-parentheses'
ON CONFLICT DO NOTHING;

INSERT INTO test_cases (problem_id, input, expected, is_hidden, order_index)
SELECT p.id, '{"s": "(]"}', 'false', false, 2
FROM problems p WHERE p.slug = 'valid-parentheses'
ON CONFLICT DO NOTHING;

INSERT INTO test_cases (problem_id, input, expected, is_hidden, order_index)
SELECT p.id, '{"s": "{[]}"}', 'true', true, 3
FROM problems p WHERE p.slug = 'valid-parentheses'
ON CONFLICT DO NOTHING;

-- Test cases for Climbing Stairs
INSERT INTO test_cases (problem_id, input, expected, is_hidden, order_index)
SELECT p.id, '{"n": 2}', '2', false, 0
FROM problems p WHERE p.slug = 'climbing-stairs'
ON CONFLICT DO NOTHING;

INSERT INTO test_cases (problem_id, input, expected, is_hidden, order_index)
SELECT p.id, '{"n": 3}', '3', false, 1
FROM problems p WHERE p.slug = 'climbing-stairs'
ON CONFLICT DO NOTHING;

INSERT INTO test_cases (problem_id, input, expected, is_hidden, order_index)
SELECT p.id, '{"n": 10}', '89', true, 2
FROM problems p WHERE p.slug = 'climbing-stairs'
ON CONFLICT DO NOTHING;

-- Test cases for Binary Search
INSERT INTO test_cases (problem_id, input, expected, is_hidden, order_index)
SELECT p.id, '{"nums": [-1, 0, 3, 5, 9, 12], "target": 9}', '4', false, 0
FROM problems p WHERE p.slug = 'binary-search'
ON CONFLICT DO NOTHING;

INSERT INTO test_cases (problem_id, input, expected, is_hidden, order_index)
SELECT p.id, '{"nums": [-1, 0, 3, 5, 9, 12], "target": 2}', '-1', false, 1
FROM problems p WHERE p.slug = 'binary-search'
ON CONFLICT DO NOTHING;

-- Problem-tag associations
INSERT INTO problem_tags (problem_id, tag_id)
SELECT p.id, t.id FROM problems p, tags t
WHERE p.slug = 'two-sum' AND t.name IN ('Array', 'Hash Table')
ON CONFLICT DO NOTHING;

INSERT INTO problem_tags (problem_id, tag_id)
SELECT p.id, t.id FROM problems p, tags t
WHERE p.slug = 'reverse-string' AND t.name IN ('String', 'Two Pointers')
ON CONFLICT DO NOTHING;

INSERT INTO problem_tags (problem_id, tag_id)
SELECT p.id, t.id FROM problems p, tags t
WHERE p.slug = 'valid-parentheses' AND t.name IN ('String', 'Stack')
ON CONFLICT DO NOTHING;

INSERT INTO problem_tags (problem_id, tag_id)
SELECT p.id, t.id FROM problems p, tags t
WHERE p.slug = 'longest-substring-without-repeating-characters' AND t.name IN ('String', 'Hash Table', 'Sliding Window')
ON CONFLICT DO NOTHING;

INSERT INTO problem_tags (problem_id, tag_id)
SELECT p.id, t.id FROM problems p, tags t
WHERE p.slug = 'merge-two-sorted-lists' AND t.name IN ('Linked List', 'Recursion')
ON CONFLICT DO NOTHING;

INSERT INTO problem_tags (problem_id, tag_id)
SELECT p.id, t.id FROM problems p, tags t
WHERE p.slug = 'maximum-subarray' AND t.name IN ('Array', 'Dynamic Programming', 'Greedy')
ON CONFLICT DO NOTHING;

INSERT INTO problem_tags (problem_id, tag_id)
SELECT p.id, t.id FROM problems p, tags t
WHERE p.slug = 'climbing-stairs' AND t.name IN ('Dynamic Programming', 'Math')
ON CONFLICT DO NOTHING;

INSERT INTO problem_tags (problem_id, tag_id)
SELECT p.id, t.id FROM problems p, tags t
WHERE p.slug = 'binary-search' AND t.name IN ('Array', 'Binary Search')
ON CONFLICT DO NOTHING;

INSERT INTO problem_tags (problem_id, tag_id)
SELECT p.id, t.id FROM problems p, tags t
WHERE p.slug = 'word-search' AND t.name IN ('Array', 'Backtracking', 'Depth-First Search')
ON CONFLICT DO NOTHING;

INSERT INTO problem_tags (problem_id, tag_id)
SELECT p.id, t.id FROM problems p, tags t
WHERE p.slug = 'median-of-two-sorted-arrays' AND t.name IN ('Array', 'Binary Search')
ON CONFLICT DO NOTHING;
