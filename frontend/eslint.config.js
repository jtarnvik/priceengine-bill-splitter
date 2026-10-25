import js from '@eslint/js'
import globals from 'globals'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'

// Copied from sl-dashboard, so the projects share one style. `node` is where frontend-maven-plugin installs
// Node and npm, whose own JavaScript must not be linted.
export default tseslint.config(
  { ignores: ['dist', 'node', '.claude'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended, react.configs.flat.recommended, react.configs.flat['jsx-runtime']],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
      parserOptions: {
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    settings: {
      react: { version: '19' },
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true },
      ],
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_' },
      ],
      'curly': 'error',
    },
  },
)
