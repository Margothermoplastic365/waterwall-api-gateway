/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  transpilePackages: ['@gateway/shared-ui'],
};

module.exports = nextConfig;
