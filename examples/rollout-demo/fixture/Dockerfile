FROM python:3.13-alpine

RUN apk add --no-cache postgresql-client
WORKDIR /app
COPY service.py /app/service.py
ENTRYPOINT ["python3", "/app/service.py"]
