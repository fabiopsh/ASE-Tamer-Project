FROM nginx:1.27-alpine
COPY gateway/nginx.conf /etc/nginx/nginx.conf
COPY frontend/ /usr/share/nginx/html/
RUN rm -f /usr/share/nginx/html/README.md
EXPOSE 80 443
