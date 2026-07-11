export default [
  {
    ignores: ['node_modules/**'],
  },
  {
    files: ['**/*.js'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: {
        Buffer: 'readonly',
        console: 'readonly',
        process: 'readonly',
      },
      sourceType: 'module',
    },
    rules: {
      'comma-dangle': ['error', 'always-multiline'],
      curly: ['error', 'multi-line'],
      eqeqeq: ['error', 'always'],
      'no-constant-binary-expression': 'error',
      'no-dupe-keys': 'error',
      'no-duplicate-case': 'error',
      'no-redeclare': 'error',
      'no-self-assign': 'error',
      'no-self-compare': 'error',
      'no-sparse-arrays': 'error',
      'no-undef': 'error',
      'no-unreachable': 'error',
      'no-unused-vars': ['error', { args: 'none', caughtErrors: 'none' }],
      'no-unsafe-finally': 'error',
      'no-unsafe-negation': 'error',
      quotes: ['error', 'single', { allowTemplateLiterals: true, avoidEscape: true }],
      semi: ['error', 'always'],
      'use-isnan': 'error',
      'valid-typeof': 'error',
    },
  },
];
