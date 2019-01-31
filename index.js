import net from 'net';

const PORT = 3002;

const server = net.createServer();

server.on('listening', () => {
    console.log('Listening on port', PORT);
});

server.on('error', (e) => {
    if (e.code === 'EADDRINUSE') {
        console.log('Port in use, retrying...');
        setTimeout(() => {
            server.close();
            server.listen(PORT);
        }, 1000);
    }
});

server.on('connection', conn => {
    conn.on('data', data => console.log(data.toString()));
});

server.listen(PORT);